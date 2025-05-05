package com.example.experiment;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "EntecDB";
    private static final int DATABASE_VERSION = 53;
    public static final String TABLE_USERS = "users";
    public static final String TABLE_COURSES = "courses";

    // Add these constants to DatabaseHelper.java
    public static final String TABLE_ACADEMIC_PROGRAMS = "academic_programs";
    public static final String TABLE_COURSE_PROGRAMS = "course_programs";

    public static final String TABLE_CAREERS = "careers";

    public static final String TABLE_ADMINISTRATORS = "administrators";
    public static final String TABLE_AVAILABILITY = "availability";
    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        try {
            // Create users table
            String CREATE_USERS_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_USERS + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "username TEXT UNIQUE, "
                    + "password TEXT, "
                    + "role TEXT)";
            db.execSQL(CREATE_USERS_TABLE);

            // Create courses table
            String CREATE_COURSES_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_COURSES + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "course TEXT UNIQUE, "
                    + "reference TEXT, "
                    + "credits TEXT, "
                    + "session TEXT, "
                    + "description TEXT, "
                    + "start_date TEXT, "
                    + "end_date TEXT, "
                    + "instructor TEXT)";
            db.execSQL(CREATE_COURSES_TABLE);

            // Create academic programs table
            String CREATE_ACADEMIC_PROGRAMS_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_ACADEMIC_PROGRAMS + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "program_name TEXT UNIQUE, "
                    + "pathway TEXT, "
                    + "description TEXT)";
            db.execSQL(CREATE_ACADEMIC_PROGRAMS_TABLE);

            // Create course_programs junction table for many-to-many relationship
            String CREATE_COURSE_PROGRAMS_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_COURSE_PROGRAMS + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "course_id INTEGER, "
                    + "program_id INTEGER, "
                    + "FOREIGN KEY(course_id) REFERENCES " + TABLE_COURSES + "(id), "
                    + "FOREIGN KEY(program_id) REFERENCES " + TABLE_ACADEMIC_PROGRAMS + "(id), "
                    + "UNIQUE(course_id, program_id))";
            db.execSQL(CREATE_COURSE_PROGRAMS_TABLE);

            // Create Careers table
            String CREATE_CAREERS_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_CAREERS + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "occupation_title TEXT UNIQUE, "
                    + "occupation_code TEXT, "
                    + "employment_2023 REAL, "  // Using REAL instead of INTEGER for decimal values
                    + "employment_percent_change REAL, "
                    + "median_annual_wage REAL, "
                    + "education_work_experience TEXT"
                    + ")";
            db.execSQL(CREATE_CAREERS_TABLE);

            // Check if users table is empty before inserting default admin
            android.database.Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_USERS, null);
            boolean shouldInsertAdmin = true;
            if (cursor != null && cursor.moveToFirst()) {
                int count = cursor.getInt(0);
                if (count > 0) {
                    shouldInsertAdmin = false;
                }
                cursor.close();
            }

            // Insert default admin account if needed
            if (shouldInsertAdmin) {
                db.execSQL("INSERT INTO users (username, password, role) VALUES ('admin', 'adminpass', 'Admin')");
            }

            String CREATE_ADMINISTRATORS_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_ADMINISTRATORS + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "name TEXT UNIQUE, "
                    + "title TEXT,"
                    + "email TEXT, "
                    + "phone TEXT, "
                    + "department TEXT"
                    + ")";
            db.execSQL(CREATE_ADMINISTRATORS_TABLE);

            String CREATE_ADMIN_AVAILABILITY_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_AVAILABILITY + "("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "admin_id INTEGER, "
                    + "date TEXT, "
                    + "time_slot TEXT, "
                    + "is_available INTEGER DEFAULT 1, "
                    + "FOREIGN KEY(admin_id) REFERENCES administrators(id), "
                    + "UNIQUE(admin_id, date, time_slot)"
                    + ")";
            db.execSQL(CREATE_ADMIN_AVAILABILITY_TABLE);
        } catch (Exception e) {
            Log.e("DatabaseHelper", "Error in onCreate", e);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Instead of dropping all tables, check if they exist first
        try {
            // Drop tables if they exist
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_CAREERS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_COURSES);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_ADMINISTRATORS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_AVAILABILITY);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_ACADEMIC_PROGRAMS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_COURSE_PROGRAMS);

            // Recreate tables
            onCreate(db);
        } catch (Exception e) {
            // Log the error but don't crash
            Log.e("DatabaseHelper", "Error in onUpgrade", e);
        }
    }

}