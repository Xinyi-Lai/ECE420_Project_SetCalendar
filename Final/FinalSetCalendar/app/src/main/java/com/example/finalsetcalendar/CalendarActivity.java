package com.example.finalsetcalendar;

import android.Manifest;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.icu.util.Calendar;
import android.icu.util.TimeZone;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class CalendarActivity extends AppCompatActivity {

    public static final int CALENDAR_REQUEST_CODE = 205;

    Button button;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);

        button = findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void onClick(View v) {
                askCalendarPermissions(CALENDAR_REQUEST_CODE);
            }
        });


    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void askCalendarPermissions(int requestCode) {
        int checkSelfPermission;
        try {
            checkSelfPermission = ActivityCompat.checkSelfPermission(this,Manifest.permission.WRITE_CALENDAR);
        } catch (RuntimeException e) {
            e.printStackTrace();
            return;
        }
        // Permission granted, add event
        if (checkSelfPermission == PackageManager.PERMISSION_GRANTED) {
            addCalendarEvent(getApplicationContext(), "Title", "description");
            return;
        } else {
            // Permission not granted, ask for permission
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.WRITE_CALENDAR,
                    Manifest.permission.READ_CALENDAR}, requestCode);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CALENDAR_REQUEST_CODE) {
            // Permission granted, add event
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                addCalendarEvent(getApplicationContext(), "Title", "description");
            } else {
                Toast.makeText(this, "Calendar Permission is Required", Toast.LENGTH_SHORT).show();
            }
        }
    }



//    // Projection array. Creating indices for this array instead of doing
//    // dynamic lookups improves performance.
//    public static final String[] EVENT_PROJECTION = new String[] {
//            CalendarContract.Calendars._ID,                           // 0
//            CalendarContract.Calendars.ACCOUNT_NAME,                  // 1
//            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,         // 2
//            CalendarContract.Calendars.OWNER_ACCOUNT                  // 3
//    };
//    // The indices for the projection array above.
//    private static final int PROJECTION_ID_INDEX = 0;
//    private static final int PROJECTION_ACCOUNT_NAME_INDEX = 1;
//    private static final int PROJECTION_DISPLAY_NAME_INDEX = 2;
//    private static final int PROJECTION_OWNER_ACCOUNT_INDEX = 3;
//
//
//
//    // Run query
//    Cursor cur = null;
//    ContentResolver cr = getContentResolver();
//    Uri uri = Calendars.CONTENT_URI;
//    String selection = "((" + Calendars.ACCOUNT_NAME + " = ?) AND ("
//            + Calendars.ACCOUNT_TYPE + " = ?) AND ("
//            + Calendars.OWNER_ACCOUNT + " = ?))";
//    String[] selectionArgs = new String[] {"hera@example.com", "com.example",
//            "hera@example.com"};
//// Submit the query and get a Cursor object back.
//    cur = cr.query(uri, EVENT_PROJECTION, selection, selectionArgs, null);



    private static String CALANDER_URL = "content://com.android.calendar/calendars";
    private static String CALANDER_EVENT_URL = "content://com.android.calendar/events";

    private static int checkCalendarAccount(Context context) {
        Cursor userCursor = context.getContentResolver().query(Uri.parse(CALANDER_URL), null, null, null, null);
        try {
            if (userCursor == null)//查询返回空值
                return -1;
            int count = userCursor.getCount();
            if (count > 0) {//存在现有账户，取第一个账户的id返回
                userCursor.moveToFirst();
                return userCursor.getInt(userCursor.getColumnIndex(CalendarContract.Calendars._ID));
            } else {
                return -1;
            }
        } finally {
            if (userCursor != null) {
                userCursor.close();
            }
        }
    }

    private static String CALENDARS_NAME = "localname";
    private static String CALENDARS_ACCOUNT_NAME = "localaccount@gmail.com";
    private static String CALENDARS_ACCOUNT_TYPE = CalendarContract.ACCOUNT_TYPE_LOCAL;
    private static String CALENDARS_DISPLAY_NAME = "localdisplay";

    @RequiresApi(api = Build.VERSION_CODES.N)
    private static long addCalendarAccount(Context context) {
        TimeZone timeZone = TimeZone.getDefault();
        ContentValues value = new ContentValues();
        value.put(CalendarContract.Calendars.NAME, CALENDARS_NAME);

        value.put(CalendarContract.Calendars.ACCOUNT_NAME, CALENDARS_ACCOUNT_NAME);
        value.put(CalendarContract.Calendars.ACCOUNT_TYPE, CALENDARS_ACCOUNT_TYPE);
        value.put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CALENDARS_DISPLAY_NAME);
        value.put(CalendarContract.Calendars.VISIBLE, 1);
        value.put(CalendarContract.Calendars.CALENDAR_COLOR, Color.BLUE);
        value.put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER);
        value.put(CalendarContract.Calendars.SYNC_EVENTS, 1);
        value.put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, timeZone.getID());
        value.put(CalendarContract.Calendars.OWNER_ACCOUNT, CALENDARS_ACCOUNT_NAME);
        value.put(CalendarContract.Calendars.CAN_ORGANIZER_RESPOND, 0);

        Uri calendarUri = Uri.parse(CALANDER_URL);
        calendarUri = calendarUri.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, CALENDARS_ACCOUNT_NAME)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CALENDARS_ACCOUNT_TYPE)
                .build();

        Uri result = context.getContentResolver().insert(calendarUri, value);
        long id = result == null ? -1 : ContentUris.parseId(result);
        return id;
    }

    //检查是否已经添加了日历账户，如果没有添加先添加一个日历账户再查询
    @RequiresApi(api = Build.VERSION_CODES.N)
    private static int checkAndAddCalendarAccount(Context context){
        int oldId = checkCalendarAccount(context);
        if( oldId >= 0 ){
            return oldId;
        }else{
            long addId = addCalendarAccount(context);
            if (addId >= 0) {
                return checkCalendarAccount(context);
            } else {
                return -1;
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public static void addCalendarEvent(Context context, String title, String description){
        // 获取日历账户的id
        int calId = checkAndAddCalendarAccount(context);
        if (calId < 0) {
            // 获取账户id失败直接返回，添加日历事件失败
            Log.d("tag", "calID<0");
            return;
        }
        Log.d("tag", "adding calID:"+calId);
        ContentValues event = new ContentValues();
        event.put("title", title);
        event.put("description", description);
        // 插入账户的id
        event.put("calendar_id", calId);

        long startMillis = 0;
        long endMills = 0;
        Calendar beginTime = Calendar.getInstance();
        beginTime.set(2020,4,29,7,30);
        startMillis = beginTime.getTimeInMillis();
        Calendar endTime = Calendar.getInstance();
        endTime.set(2020,4,29,8,30);
        endMills = endTime.getTimeInMillis();

        event.put(CalendarContract.Events.DTSTART, startMillis);
        event.put(CalendarContract.Events.DTEND, endMills);
        event.put(CalendarContract.Events.HAS_ALARM, 1);//设置有闹钟提醒
        event.put(CalendarContract.Events.EVENT_TIMEZONE, "America/Los_Angeles");  //这个是时区，必须有，

        //添加事件
        Uri newEvent = context.getContentResolver().insert(Uri.parse(CALANDER_EVENT_URL), event);
        if (newEvent == null) {
            // 添加日历事件失败直接返回
            Log.d("tag", "fail to insert new event");
            return;
        }
        Log.d("tag", "succeed to insert new event");

    }

    public static void deleteCalendarEvent(Context context,String title){
        Cursor eventCursor = context.getContentResolver().query(Uri.parse(CALANDER_EVENT_URL), null, null, null, null);
        try {
            if (eventCursor == null)//查询返回空值
                return;
            if (eventCursor.getCount() > 0) {
                //遍历所有事件，找到title跟需要查询的title一样的项
                for (eventCursor.moveToFirst(); !eventCursor.isAfterLast(); eventCursor.moveToNext()) {
                    String eventTitle = eventCursor.getString(eventCursor.getColumnIndex("title"));
                    if (!TextUtils.isEmpty(title) && title.equals(eventTitle)) {
                        int id = eventCursor.getInt(eventCursor.getColumnIndex(CalendarContract.Calendars._ID));//取得id
                        Uri deleteUri = ContentUris.withAppendedId(Uri.parse(CALANDER_EVENT_URL), id);
                        int rows = context.getContentResolver().delete(deleteUri, null, null);
                        if (rows == -1) {
                            //事件删除失败
                            return;
                        }
                    }
                }
            }
        } finally {
            if (eventCursor != null) {
                eventCursor.close();
            }
        }
    }

//    public static void readCalendarEvent() {
//        Toast.makeText(this, "In Query", Toast.LENGTH_SHORT).show();
//        // Run query
//        Cursor cur = null;
//        ContentResolver cr = getContentResolver();
//        Uri uri = CalendarContract.Calendars.CONTENT_URI;
//        String selection = "((" + CalendarContract.Calendars.ACCOUNT_NAME + " = ?) AND ("
//                + CalendarContract.Calendars.ACCOUNT_TYPE + " = ?) AND ("
//                + CalendarContract.Calendars.OWNER_ACCOUNT + " = ?))";
//        String[] selectionArgs = new String[]{"hera@example.com", "com.example", "hera@example.com"};
//        // Submit the query and get a Cursor object back.
//        cur = cr.query(uri, EVENT_PROJECTION, selection, selectionArgs, null);
//        Log.d("tag", "while");
//        while (cur.moveToNext()) {
//            Log.d("tag", "in while");
//            if (cur!=null){
//                int id_1 = cur.getColumnIndex(CalendarContract.Events._ID);
//                int id_2 = cur.getColumnIndex(CalendarContract.Events.TITLE);
//                int id_3 = cur.getColumnIndex(CalendarContract.Events.DESCRIPTION);
//                int id_4 = cur.getColumnIndex(CalendarContract.Events.EVENT_LOCATION);
//
//                String idValue = cur.getColumnName(id_1);
//                String titleValue = cur.getString(id_2);
//                String descriptionValue = cur.getString((id_3));
//                String eventValue = cur.getString(id_4);
//
//                Toast.makeText(this, idValue + ", " + titleValue + ", " + descriptionValue + ", " + eventValue, Toast.LENGTH_SHORT).show();
//    }


//    // Projection array. Creating indices for this array instead of doing
//    // dynamic lookups improves performance.
//    public static final String[] EVENT_PROJECTION = new String[]{
//            CalendarContract.Calendars._ID,                           // 0
//            CalendarContract.Calendars.ACCOUNT_NAME,                  // 1
//            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,         // 2
//            CalendarContract.Calendars.OWNER_ACCOUNT                  // 3
//    };
//
//    // The indices for the projection array above.
//    private static final int PROJECTION_ID_INDEX = 0;
//    private static final int PROJECTION_ACCOUNT_NAME_INDEX = 1;
//    private static final int PROJECTION_DISPLAY_NAME_INDEX = 2;
//    private static final int PROJECTION_OWNER_ACCOUNT_INDEX = 3;








//    @RequiresApi(api = Build.VERSION_CODES.N)
//    private void askCalendarPermissions() {
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CALENDAR}, CALENDAR_READ_PERM_CODE);
//        }
//        else if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED){
//            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_CALENDAR}, CALENDAR_WRITE_PERM_CODE);
//        }
//        else {
//            addCalendarEvent(getApplicationContext(), "Title", "description");
//        }
//    }

//    @RequiresApi(api = Build.VERSION_CODES.N)
//    @Override
//    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
//        if (true) {
//            if (grantResults.length < 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                Toast.makeText(CalendarActivity.this, "Here", Toast.LENGTH_SHORT).show();
//                addCalendarEvent(getApplicationContext(), "Title", "description");
//            } else {
//                Toast.makeText(CalendarActivity.this, "Camera Permission is Required", Toast.LENGTH_SHORT).show();
//            }
//        }
//    }

//    private void CalendarQuery() {
//        Toast.makeText(CalendarActivity.this, "In Query", Toast.LENGTH_SHORT).show();
//        // Run query
//        Cursor cur = null;
//        ContentResolver cr = getContentResolver();
//        Uri uri = CalendarContract.Calendars.CONTENT_URI;
//        String selection = "((" + CalendarContract.Calendars.ACCOUNT_NAME + " = ?) AND ("
//                + CalendarContract.Calendars.ACCOUNT_TYPE + " = ?) AND ("
//                + CalendarContract.Calendars.OWNER_ACCOUNT + " = ?))";
//        String[] selectionArgs = new String[]{"hera@example.com", "com.example", "hera@example.com"};
//        // Submit the query and get a Cursor object back.
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
//            // TODO: Consider calling
//            //    ActivityCompat#requestPermissions
//            // here to request the missing permissions, and then overriding
//            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
//            //                                          int[] grantResults)
//            // to handle the case where the user grants the permission. See the documentation
//            // for ActivityCompat#requestPermissions for more details.
//            return;
//        }
//        cur = cr.query(uri, EVENT_PROJECTION, selection, selectionArgs, null);
//        Log.d("tag", "while");
//        while (cur.moveToNext()) {
//            Log.d("tag", "in while");
//            if (cur!=null){
//                int id_1 = cur.getColumnIndex(CalendarContract.Events._ID);
//                int id_2 = cur.getColumnIndex(CalendarContract.Events.TITLE);
//                int id_3 = cur.getColumnIndex(CalendarContract.Events.DESCRIPTION);
//                int id_4 = cur.getColumnIndex(CalendarContract.Events.EVENT_LOCATION);
//
//                String idValue = cur.getColumnName(id_1);
//                String titleValue = cur.getString(id_2);
//                String descriptionValue = cur.getString((id_3));
//                String eventValue = cur.getString(id_4);
//
//                Toast.makeText(this, idValue + ", " + titleValue + ", " + descriptionValue + ", " + eventValue, Toast.LENGTH_SHORT).show();
//            } else {
//                Toast.makeText(this, "Event is not present", Toast.LENGTH_SHORT).show();
//            }
//        }
//    }

}
