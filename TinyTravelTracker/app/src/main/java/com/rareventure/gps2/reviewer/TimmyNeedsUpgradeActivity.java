/** 
    Copyright 2015 Tim Engler, Rareventure LLC

    This file is part of Tiny Travel Tracker.

    Tiny Travel Tracker is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Tiny Travel Tracker is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Tiny Travel Tracker.  If not, see <http://www.gnu.org/licenses/>.

*/
package com.rareventure.gps2.reviewer;

import java.io.IOException;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.rareventure.android.ProgressDialogActivity;
import com.rareventure.gps2.GTG;
import com.rareventure.gps2.GpsTrailerDbProvider;
import com.rareventure.gps2.R;
import com.rareventure.gps2.GTG.Requirement;

public class TimmyNeedsUpgradeActivity extends ProgressDialogActivity
{
	public TimmyNeedsUpgradeActivity()
	{
	}
	

	@Override
	public void doOnCreate(Bundle savedInstanceState) {
		super.doOnCreate(savedInstanceState);
		setContentView(R.layout.timmy_needs_upgrade);
	}


	@Override
	public void doOnResume()
	{
		super.doOnResume();
	}

	public void onOk(View view) {
		super.runLongTask(new Task()
		{

			@Override
			public void doIt() {
				try {
					if(GTG.timmyDb != null)
						GTG.timmyDb.close();
					
					GpsTrailerDbProvider.deleteUnopenedCache();
				} catch (IOException e) {
					throw new IllegalStateException(e);
				}
				GTG.timmyDb = null;
				Requirement.TIMMY_DB_READY.reset();
			}
			
			@Override
			public void doAfterFinish() {
				finish();
			}
			
			
		}, false, true, R.string.dialog_long_task_title,
		R.string.please_wait);
	}
	
	@Override
	public int getRequirements() {
		return GTG.REQUIREMENTS_BASIC_PASSWORD_PROTECTED_UI;
	}

}
