package com.bar.imageloader;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.ContextCompat;
import android.view.View;

import com.bar.R;
import com.bar.imageloader.stubs.DefaultLoadingDrawable;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ImageLoader implements ImageDownloader.DownloadEventListener {

    private static ImageLoader instance = null;

    public static synchronized ImageLoader getInstance(Context context) {
        if(instance == null)
            instance = new ImageLoader(context);

        return instance;
    }

    private static final String PREFS = "ImageLoaderPrefs";

    protected Context context;
    protected FileCache fileCache;
    protected Map<View, String> views = Collections.synchronizedMap(new WeakHashMap<View, String>());

    protected Map<String, ImageDownloader> downloadRequests = new HashMap<String, ImageDownloader>();
    protected Map<String, List<ImageRequest>> delayedRequests = new HashMap<String, List<ImageRequest>>();
    protected ExecutorService executorService;

    private StubHolder stubHolder;
    private final Handler handler = new Handler(Looper.getMainLooper());

    protected ImageLoader(Context context) {
        this.context = context.getApplicationContext();
        this.fileCache = new FileCache(context);
        this.executorService = Executors.newFixedThreadPool(context.getResources().getInteger(R.integer.ail__thread_pool_size));
    }

    /**
     * Submit an ImageRequest to the ImageLoader's downloading service for managed downloads
     */
    public void submit(ImageRequest request){
        if(request.isTargetLocal()
                || (isImageDownloaded(request) && fileCache.isCachedFileValid(request.getTargetUrl(), request.getMaxCacheDurationMs())))
            executorService.submit(request);
        else submitDownloadRequest(request);
    }

    protected void submitDownloadRequest(ImageRequest request){
        if(delayedRequests.get(request.getTargetUrl()) == null)
            delayedRequests.put(request.getTargetUrl(), new ArrayList<ImageRequest>());

        delayedRequests.get(request.getTargetUrl())
                .add(request);

        context.getSharedPreferences(PREFS, 0)
                .edit()
                .putBoolean(request.getTargetUrl(), false)
                .commit(); // Prevent deletions from messing up

        if(downloadRequests.get(request.getTargetUrl()) == null){
            ImageDownloader downloadRequest = new ImageDownloader(handler, request, this);
            downloadRequests.put(request.getTargetUrl(), downloadRequest);

            executorService.submit(downloadRequest);
        }
    }

    @Override
    public synchronized void onDownloadCompleted(ImageDownloader downloadRequest, String targetUrl) {
        context.getSharedPreferences(PREFS, 0)
                .edit()
                .putBoolean(targetUrl, true)
                .commit();

        if(delayedRequests.get(targetUrl) != null){
            for(ImageRequest request : delayedRequests.get(targetUrl))
                executorService.submit(request);

            delayedRequests.remove(targetUrl);
        }

        downloadRequests.remove(targetUrl);
    }

    @Override
    public synchronized void onDownloadFailed(ImageDownloader downloadRequest, String targetUrl) {
        if(delayedRequests.get(targetUrl) != null){
            // For now, just execute pending requests, as they won't do anything but set appropriate stub
            for(ImageRequest request : delayedRequests.get(targetUrl))
                executorService.submit(request);

            delayedRequests.remove(targetUrl);
        }

        downloadRequests.remove(targetUrl);
    }

    /**
     * Perform the download for the supplied ImageRequest and return the File containing the result
     */
    public File download(ImageRequest request){
        try{
            HttpURLConnection conn = openImageDownloadConnection(getCorrectDownloadUrl(request.getTargetUrl()));
            setHttpRequestProperties(request, conn);

            return download(conn, request.getOriginalRequestFile());
        }
        catch(Throwable ex){ ex.printStackTrace(); }

        return null;
    }

    @Deprecated
    public File download(String url, File file) {
        try{
            return download(openImageDownloadConnection(getCorrectDownloadUrl(url)), file);
        }
        catch(Throwable ex){ ex.printStackTrace(); }

        return null;
    }

    /**
     * Perform the download of the supplied HttpURLConnection into the File parameter
     * supplied and return the File containing the result (same as param).
     */
    public File download(HttpURLConnection conn, File file){
        InputStream is = null;

        try{
            is = conn.getInputStream();
            ImageUtils.saveStream(file, is);

            return file;
        }
        catch(Throwable ex){ ex.printStackTrace(); }
        finally{ closeConnection(is); }

        return null;
    }

    protected URL getCorrectDownloadUrl(String url) throws Exception {
        URL imageUrl = new URL(url);

        for(String regex : Arrays.asList(context.getResources().getStringArray(R.array.ail__known_location_redirects_regex))){
            Matcher matcher = Pattern.compile(regex)
                    .matcher(url);

            if(matcher.find())
                return getAdjustedLocationImageUrl(imageUrl);
        }

        return imageUrl;
    }

    protected URL getAdjustedLocationImageUrl(URL imageUrl) throws Exception {
        HttpURLConnection.setFollowRedirects(false);
        HttpURLConnection conn = (HttpURLConnection) imageUrl.openConnection();

        URL adjustedImageUrl = new URL(conn.getHeaderField("Location"));
        conn.disconnect();

        HttpURLConnection.setFollowRedirects(true);

        return adjustedImageUrl;
    }

    protected HttpURLConnection openImageDownloadConnection(URL url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);
        conn.setInstanceFollowRedirects(true);

        return conn;
    }

    protected void setHttpRequestProperties(ImageRequest request, HttpURLConnection conn){
        Map<String, String> requestParams = request.getHttpRequestParams();

        for(String key : requestParams.keySet())
            conn.setRequestProperty(key, requestParams.get(key));
    }

    public static void closeConnection(InputStream is) {
        try{
            if(is != null)
                is.close();
        }
        catch(Exception e){ }
    }

    public void claimViewTarget(ImageRequest request) {
        claimViewTarget(request.getTargetView(),
                request.getTargetUrl(),
                request.getStartedAtMs());
    }

    private void claimViewTarget(View view, String targetUrl, long startedAtMs) {
        views.put(view, combineViewNameParams(targetUrl, startedAtMs));
    }

    public boolean isViewStillUsable(ImageRequest request) {
        return isViewStillUsable(request.getTargetView(),
                request.getTargetUrl(),
                request.getStartedAtMs());
    }

    private boolean isViewStillUsable(View target, String targetUrl, long startedAtMs) {
        return combineViewNameParams(targetUrl, startedAtMs)
                .equals(views.get(target));
    }

    private String combineViewNameParams(String targetUrl, long startedAtMs){
        return targetUrl + "_" + startedAtMs;
    }

    public FileCache getFileCache() {
        return fileCache;
    }

    public StubHolder getStubs() {
        if(stubHolder == null)
            return defaultStubHolder;
        else return stubHolder;
    }

    public StubHolder getResourceBasedStubs() {
        if(stubHolder == null)
            return defaultResourceStubHolder;
        else return stubHolder;
    }

    public void registerStubs(StubHolder stubHolder) {
        this.stubHolder = stubHolder;
    }

    public boolean isImageDownloaded(ImageRequest request){
        return request.getOriginalRequestFile().exists()
                && context.getSharedPreferences(PREFS, 0).getBoolean(request.getTargetUrl(), false);
    }

    public Handler getHandler(){
        return handler;
    }

    private final StubHolder defaultStubHolder = new StubHolder(){

        @Override
        public Drawable getLoadingDrawable(Context context) {
            return new DefaultLoadingDrawable(context.getResources());
        }

        @Override
        public Drawable getErrorDrawable(Context context) {
            return ContextCompat.getDrawable(context, R.drawable.ail__image_loader_stub_error);
        }
    };

    private final StubHolder defaultResourceStubHolder = new StubHolder(){

        @Override
        public Drawable getLoadingDrawable(Context context) {
            return ContextCompat.getDrawable(context,
                    R.drawable.ail__image_loader_stub);
        }

        @Override
        public Drawable getErrorDrawable(Context context) {
            return ContextCompat.getDrawable(context,
                    R.drawable.ail__image_loader_stub_error);
        }
    };
}
