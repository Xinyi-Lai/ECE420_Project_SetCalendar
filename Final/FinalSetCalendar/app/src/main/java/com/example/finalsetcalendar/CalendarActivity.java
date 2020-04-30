package com.example.finalsetcalendar;

import android.Manifest;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class CalendarActivity extends AppCompatActivity {

    public static final int CALENDAR_PERM_CODE = 201;
    public static final int CALENDAE_REQUEST_CODE = 202;

    // Projection array. Creating indices for this array instead of doing
    // dynamic lookups improves performance.
    public static final String[] EVENT_PROJECTION = new String[]{
            CalendarContract.Calendars._ID,                           // 0
            CalendarContract.Calendars.ACCOUNT_NAME,                  // 1
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,         // 2
            CalendarContract.Calendars.OWNER_ACCOUNT                  // 3
    };

    // The indices for the projection array above.
    private static final int PROJECTION_ID_INDEX = 0;
    private static final int PROJECTION_ACCOUNT_NAME_INDEX = 1;
    private static final int PROJECTION_DISPLAY_NAME_INDEX = 2;
    private static final int PROJECTION_OWNER_ACCOUNT_INDEX = 3;


    Button button;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);

        button = findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askCalendarPermissions();
            }
        });


    }

    private void askCalendarPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CALENDAR}, CALENDAR_PERM_CODE);
        } else {
            CalendarQuery();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == CALENDAR_PERM_CODE) {
            if (grantResults.length < 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                CalendarQuery();
            } else {
                Toast.makeText(CalendarActivity.this, "Camera Permission is Required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void CalendarQuery() {
        Toast.makeText(CalendarActivity.this, "In Query", Toast.LENGTH_SHORT).show();
        // Run query
        Cursor cur = null;
        ContentResolver cr = getContentResolver();
        Uri uri = CalendarContract.Calendars.CONTENT_URI;
        String selection = "((" + CalendarContract.Calendars.ACCOUNT_NAME + " = ?) AND ("
                + CalendarContract.Calendars.ACCOUNT_TYPE + " = ?) AND ("
                + CalendarContract.Calendars.OWNER_ACCOUNT + " = ?))";
        String[] selectionArgs = new String[]{"hera@example.com", "com.example", "hera@example.com"};
        // Submit the query and get a Cursor object back.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        cur = cr.query(uri, EVENT_PROJECTION, selection, selectionArgs, null);
        Log.d("tag", "while");
        while (cur.moveToNext()) {
            Log.d("tag", "in while");
            if (cur!=null){
                int id_1 = cur.getColumnIndex(CalendarContract.Events._ID);
                int id_2 = cur.getColumnIndex(CalendarContract.Events.TITLE);
                int id_3 = cur.getColumnIndex(CalendarContract.Events.DESCRIPTION);
                int id_4 = cur.getColumnIndex(CalendarContract.Events.EVENT_LOCATION);

                String idValue = cur.getColumnName(id_1);
                String titleValue = cur.getString(id_2);
                String descriptionValue = cur.getString((id_3));
                String eventValue = cur.getString(id_4);

                Toast.makeText(this, idValue + ", " + titleValue + ", " + descriptionValue + ", " + eventValue, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Event is not present", Toast.LENGTH_SHORT).show();
            }
        }
    }

}
