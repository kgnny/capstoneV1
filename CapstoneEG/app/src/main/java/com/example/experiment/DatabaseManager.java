package com.example.experiment;

import static com.example.experiment.DatabaseHelper.TABLE_CAREERS;
import static com.example.experiment.DatabaseHelper.TABLE_COURSES;

import android.content.Context;
import android.database.Cursor;
import android.content.ContentValues;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseLockedException;
import android.util.Log;
import android.text.TextUtils;
import android.app.ProgressDialog;

import java.util.ArrayList;
import java.util.List;


public class DatabaseManager {
    private static DatabaseManager instance;
    private final Object dbLock = new Object();
    private DatabaseHelper dbHelper;
    private Context context;
    private SQLiteDatabase readableDb;
    private SQLiteDatabase writableDb;

    // Private constructor to enforce singleton pattern
    DatabaseManager(Context context) {
        this.context = context.getApplicationContext(); // Use application context to avoid leaks
        this.dbHelper = new DatabaseHelper(this.context);
    }

    // Singleton accessor
    public static synchronized DatabaseManager getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseManager(context);
        } else if (instance.dbHelper == null) {
            // Reinitialize the helper if it was closed
            instance.context = context.getApplicationContext();
            instance.dbHelper = new DatabaseHelper(instance.context);
        }
        return instance;
    }

    // Make sure dbHelper is initialized before use
    private void ensureDbHelperExists() {
        synchronized (dbLock) {
            if (dbHelper == null) {
                dbHelper = new DatabaseHelper(context);
            }
        }
    }

    private SQLiteDatabase getReadableDatabase() {
        synchronized (dbLock) {
            Log.d("DatabaseManager", "getReadableDatabase called");
            ensureDbHelperExists();

            try {
                if (readableDb == null || !readableDb.isOpen()) {
                    if (dbHelper == null) {
                        Log.e("DatabaseManager", "dbHelper is null in getReadableDatabase!");
                        dbHelper = new DatabaseHelper(context);
                    }

                    Log.d("DatabaseManager", "Creating new readable database connection");
                    readableDb = dbHelper.getReadableDatabase();
                } else {
                    Log.d("DatabaseManager", "Reusing existing readable database connection");
                }

                return readableDb;
            } catch (Exception e) {
                Log.e("DatabaseManager", "Error getting readable database", e);

                // Try to recover
                try {
                    Log.d("DatabaseManager", "Attempting database recovery");
                    closeInternal();
                    ensureDbHelperExists();
                    readableDb = dbHelper.getReadableDatabase();
                    return readableDb;
                } catch (Exception ex) {
                    Log.e("DatabaseManager", "Recovery failed", ex);
                    throw ex;
                }
            }
        }
    } // Add this closing brace to end the getReadableDatabase method


    // Modify the close method to not null out dbHelper
    public void close() {
        synchronized (dbLock) {
            closeInternal();

            // Don't set dbHelper to null
            // dbHelper = null; // Remove this line

            // Don't clear the singleton instance
            // instance = null; // Remove this line
        }
    }

    // Internal method to close all connections
    private void closeInternal() {
        try {
            if (readableDb != null && readableDb.isOpen()) {
                readableDb.close();
                readableDb = null;
            }
        } catch (Exception e) {
            Log.e("DatabaseManager", "Error closing readable database", e);
        }

        try {
            if (writableDb != null && writableDb.isOpen()) {
                writableDb.close();
                writableDb = null;
            }
        } catch (Exception e) {
            Log.e("DatabaseManager", "Error closing writable database", e);
        }
    }


    // USER OPERATIONS

    public String authenticateUser(String username, String password) {
        SQLiteDatabase db = getReadableDatabase();
        String role = null;

        Cursor cursor = db.rawQuery(
                "SELECT role FROM users WHERE username = ? AND password = ?",
                new String[]{username, password});

        if (cursor.moveToFirst()) {
            role = cursor.getString(0);
        }

        cursor.close();
        db.close();
        return role;
    }

    public boolean registerUser(String username, String password, String role) {
        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("username", username);
        values.put("password", password);
        values.put("role", role);

        long result = db.insert("users", null, values);
        db.close();

        return result != -1;
    }

    public boolean checkUserExists(String username) {
        SQLiteDatabase db = getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT id FROM users WHERE username = ?",
                new String[]{username});

        boolean exists = cursor.getCount() > 0;

        cursor.close();
        db.close();
        return exists;
    }

    public int getUserIdByUsername(String username) {
        SQLiteDatabase db = getReadableDatabase();
        int userId = -1;

        Cursor cursor = db.rawQuery(
                "SELECT id FROM users WHERE username = ?",
                new String[]{username});

        if (cursor.moveToFirst()) {
            userId = cursor.getInt(0);
        }

        cursor.close();
        db.close();
        return userId;
    }

    // COURSE OPERATIONS

    // IMPORT METHODS FOR COURSES
    public boolean importCoursesFromCSV(String[] csvLines) {
        boolean success = true;
        int insertCount = 0;

        synchronized (dbLock) {
            SQLiteDatabase db = getWritableDatabase();
            try {
                db.beginTransaction();

                // Make sure major column exists
                ensureCoursesTableColumns();

                // Check if courses already exist
                Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_COURSES, null);
                int courseCount = 0;
                if (cursor.moveToFirst()) {
                    courseCount = cursor.getInt(0);
                }
                cursor.close();

                // Only proceed with import if no courses exist
                if (courseCount == 0) {
                    // First line is header, so start from index 1
                    for (int i = 1; i < csvLines.length; i++) {
                        // Assuming tab-delimited format like your other CSVs
                        String[] values = csvLines[i].trim().split("\t");

                        if (values.length >= 8) { // Ensure we have all columns
                            ContentValues contentValues = new ContentValues();
                            contentValues.put("course", values[0].trim());
                            contentValues.put("reference", values[1].trim());
                            contentValues.put("credits", values[2].trim());
                            contentValues.put("session", values[3].trim());
                            contentValues.put("description", values[4].trim());
                            contentValues.put("start_date", values[5].trim());
                            contentValues.put("end_date", values[6].trim());
                            contentValues.put("instructor", values[7].trim());

                            // Add major if it exists in the CSV
                            if (values.length >= 9) {
                                contentValues.put("major", values[8].trim());
                            } else {
                                // Determine major based on course name or reference code
                                String course = values[0].trim().toLowerCase();
                                String ref = values[1].trim().toLowerCase();

                                // Simple major detection (improve this based on your actual course data)
                                if (course.contains("art") || course.contains("design") ||
                                        course.contains("music") || ref.startsWith("art")) {
                                    contentValues.put("major", "Arts");
                                } else if (course.contains("bio") || course.contains("chem") ||
                                        course.contains("phys") || course.contains("math") ||
                                        course.contains("computer") || ref.startsWith("sci")) {
                                    contentValues.put("major", "STEM");
                                } else if (course.contains("business") || course.contains("account") ||
                                        course.contains("finance") || course.contains("market")) {
                                    contentValues.put("major", "Business");
                                } else if (course.contains("edu") || course.contains("teach")) {
                                    contentValues.put("major", "Education");
                                } else if (course.contains("health") || course.contains("nursing") ||
                                        course.contains("med")) {
                                    contentValues.put("major", "Health Science");
                                } else {
                                    contentValues.put("major", "STEM"); // Default value
                                }
                            }

                            long result = db.insert(TABLE_COURSES, null, contentValues);
                            if (result != -1) {
                                insertCount++;
                            }
                        } else {
                            Log.e("CSV Import", "Line " + i + " has insufficient columns: " + csvLines[i]);
                        }
                    }
                }

                db.setTransactionSuccessful();
                Log.i("CSV Import", "Successfully imported " + insertCount + " courses");
            } catch (Exception e) {
                success = false;
                Log.e("CSV Import", "Error importing courses", e);
            } finally {
                if (db.inTransaction()) {
                    try {
                        db.endTransaction();
                    } catch (Exception e) {
                        Log.e("CSV Import", "Error ending transaction", e);
                    }
                }
            }
        }

        return success;
    }

    public Cursor getCourseByTitle(String courseTitle) {
        synchronized (dbLock) {
            SQLiteDatabase db = getReadableDatabase();
            try {
                // First try with "course" column (since that's what you use in your schema)
                Cursor cursor = db.query(
                        TABLE_COURSES,
                        new String[]{"id as _id", "course", "reference", "credits", "description"},
                        "course = ?",
                        new String[]{courseTitle},
                        null, null, null);

                if (cursor.getCount() == 0) {
                    cursor.close();
                    // Try with "title" column as fallback
                    cursor = db.query(
                            TABLE_COURSES,
                            new String[]{"id as _id", "course as title", "reference", "credits", "description"},
                            "course = ?",
                            new String[]{courseTitle},
                            null, null, null);
                }

                return cursor;
            } catch (Exception e) {
                Log.e("DatabaseManager", "Error getting course by title", e);
                return null;
            }
        }
    }

    //  Method to get courses by field/major
    public Cursor getCoursesByField(String field) {
        synchronized (dbLock) {
            try {
                // Safety check - create a fresh connection if needed
                SQLiteDatabase db;
                try {
                    db = getReadableDatabase();
                } catch (IllegalStateException e) {
                    // Connection pool closed - recreate the db helper
                    Log.e("DatabaseManager", "Database connection pool closed, recreating", e);
                    dbHelper = new DatabaseHelper(context);
                    db = dbHelper.getReadableDatabase();
                }

                // Safety check
                if (field == null || "All Fields".equals(field)) {
                    return getAllCourses();
                }

                Log.d("DatabaseManager", "Querying courses with major = " + field);

                // First check if any courses exist with this major
                Cursor checkCursor = null;
                int count = 0;

                try {
                    checkCursor = db.rawQuery(
                            "SELECT COUNT(*) FROM " + TABLE_COURSES + " WHERE major = ?",
                            new String[]{field}
                    );

                    if (checkCursor != null && checkCursor.moveToFirst()) {
                        count = checkCursor.getInt(0);
                    }
                } catch (Exception e) {
                    Log.e("DatabaseManager", "Error checking course count for major: " + field, e);
                } finally {
                    if (checkCursor != null) {
                        checkCursor.close();
                    }
                }

                if (count == 0) {
                    Log.d("DatabaseManager", "No courses found with major = " + field + ", returning empty cursor");
                    // Return empty cursor if no courses with this major
                    return db.rawQuery(
                            "SELECT id as _id, course, reference, description, credits, major " +
                                    "FROM " + TABLE_COURSES + " WHERE 0=1", null);
                }

                // Get courses for the given major
                Cursor cursor = db.query(
                        TABLE_COURSES,
                        new String[]{"id as _id", "course", "reference", "description", "credits", "major"},
                        "major = ?",
                        new String[]{field},
                        null, null,
                        "course ASC"
                );

                // Log found courses
                if (cursor != null) {
                    Log.d("DatabaseManager", "Found " + cursor.getCount() + " courses with major = " + field);
                }

                return cursor;
            } catch (Exception e) {
                Log.e("DatabaseManager", "Error getting courses by field: " + field, e);

                // Try one more time with a fresh database connection
                try {
                    // Force recreate the database connection
                    closeInternal();
                    dbHelper = new DatabaseHelper(context);
                    SQLiteDatabase db = dbHelper.getReadableDatabase();

                    // Return empty cursor as fallback
                    return db.rawQuery(
                            "SELECT id as _id, course, reference, description, credits " +
                                    "FROM " + TABLE_COURSES + " WHERE 0=1", null);
                } catch (Exception ex) {
                    Log.e("DatabaseManager", "Critical failure getting courses", ex);
                    // We have no choice but to return null - the calling code must handle this
                    return null;
                }
            }
        }
    }


    public Cursor getCoursesForCareer(int careerId, String field) {
        synchronized (dbLock) {
            SQLiteDatabase db = getReadableDatabase();

            try {
                // Base query for courses related to a specific career
                String baseQuery = getString(field);

                // Prepare arguments based on whether field is filtered
                String[] args = (field != null && !"All Fields".equals(field))
                        ? new String[]{String.valueOf(careerId), field}
                        : new String[]{String.valueOf(careerId)};

                return db.rawQuery(baseQuery, args);
            } catch (Exception e) {
                Log.e("DatabaseManager", "Error getting courses for career", e);
                return null;
            }
        }
    }

    private static String getString(String field) {
        String baseQuery =
                "SELECT DISTINCT c.id as _id, " +
                        "c.course || ' - ' || c.reference AS full_course_name, " +
                        "c.credits, c.description " +
                        "FROM " + TABLE_COURSES + " c " +
                        "JOIN career_courses cc ON c.id = cc.course_id " +
                        "WHERE cc.career_id = ?";

        // If a specific field is selected, add field filter
        if (field != null && !"All Fields".equals(field)) {
            baseQuery += " AND c.major = ?";
        }

        baseQuery += " ORDER BY c.course";
        return baseQuery;
    }


    public int getCourseCount() {
        synchronized (dbLock) {
            SQLiteDatabase db = getReadableDatabase();
            Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_COURSES, null);
            int count = 0;
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
            cursor.close();
            return count;
        }
    }

    public void clearCoursesTable() {
        synchronized (dbLock) {
            SQLiteDatabase db = getWritableDatabase();
            db.execSQL("DELETE FROM " + TABLE_COURSES);
            // Don't close the database here
        }
    }

    public Cursor getAllCourses() {
        synchronized (dbLock) {
            Log.d("DatabaseManager", "getAllCourses called");
            SQLiteDatabase db = null;
            Cursor cursor = null;

            try {
                // Ensure dbHelper exists
                ensureDbHelperExists();

                // Get database reference with extra logging
                db = getReadableDatabase();
                Log.d("DatabaseManager", "Got readable database: " + (db != null ? "valid" : "null"));

                if (db == null || !db.isOpen()) {
                    Log.e("DatabaseManager", "Database not available in getAllCourses");
                    return createEmptyCursor(); // Return empty cursor instead of null
                }

                // Attempt to execute query with try-catch
                try {
                    cursor = db.query(
                            TABLE_COURSES,
                            new String[]{"id as _id", "course", "reference", "credits", "description",
                                    "session", "start_date", "end_date", "instructor"},
                            null, null, null, null, "course ASC");

                    Log.d("DatabaseManager", "Query executed, cursor: " +
                            (cursor != null ? "valid with " + cursor.getCount() + " items" : "null"));

                    return cursor;
                } catch (Exception e) {
                    Log.e("DatabaseManager", "Error executing query", e);

                    // Try a simpler fallback query
                    try {
                        Log.d("DatabaseManager", "Trying fallback query");
                        cursor = db.rawQuery("SELECT id as _id, course, reference, description FROM " +
                                TABLE_COURSES + " ORDER BY course ASC", null);

                        if (cursor != null) {
                            Log.d("DatabaseManager", "Fallback query successful");
                            return cursor;
                        }
                    } catch (Exception ex) {
                        Log.e("DatabaseManager", "Fallback query failed", ex);
                    }

                    return createEmptyCursor();
                }
            } catch (Exception e) {
                Log.e("DatabaseManager", "Unexpected error in getAllCourses", e);

                if (cursor != null && !cursor.isClosed()) {
                    try {
                        cursor.close();
                    } catch (Exception ex) {
                        // Ignore
                    }
                }

                return createEmptyCursor();
            }
        }
    }

    // Helper method to create an empty cursor
    private Cursor createEmptyCursor() {
        try {
            Log.d("DatabaseManager", "Creating empty cursor");
            MatrixCursor emptyCursor = new MatrixCursor(
                    new String[]{"_id", "course", "reference", "credits", "description",
                            "session", "start_date", "end_date", "instructor"});
            return emptyCursor;
        } catch (Exception e) {
            Log.e("DatabaseManager", "Error creating empty cursor", e);
            return null;
        }
    }

    public void ensureCoursesTableColumns() {
        synchronized (dbLock) {
            SQLiteDatabase db = getWritableDatabase();

            try {
                // Check if major column exists
                Cursor cursor = db.rawQuery("PRAGMA table_info(" + TABLE_COURSES + ")", null);
                boolean majorColumnExists = false;

                if (cursor != null) {
                    int nameIndex = cursor.getColumnIndex("name");
                    while (cursor.moveToNext()) {
                        String columnName = cursor.getString(nameIndex);
                        if ("major".equals(columnName)) {
                            majorColumnExists = true;
                            break;
                        }
                    }
                    cursor.close();
                }

                // Add major column if it doesn't exist
                if (!majorColumnExists) {
                    Log.d("DatabaseManager", "Adding major column to courses table");
                    db.execSQL("ALTER TABLE " + TABLE_COURSES + " ADD COLUMN major TEXT DEFAULT 'STEM'");
                    Log.d("DatabaseManager", "Added major column to courses table");
                } else {
                    Log.d("DatabaseManager", "Major column already exists in courses table");
                }

                // Verify all courses have a major assigned
                Cursor courseCheck = db.rawQuery(
                        "SELECT COUNT(*) FROM " + TABLE_COURSES + " WHERE major IS NULL OR major = ''",
                        null
                );

                if (courseCheck != null && courseCheck.moveToFirst()) {
                    int nullMajorCount = courseCheck.getInt(0);
                    if (nullMajorCount > 0) {
                        Log.d("DatabaseManager", "Found " + nullMajorCount + " courses with null major, setting to STEM");
                        db.execSQL("UPDATE " + TABLE_COURSES + " SET major = 'STEM' WHERE major IS NULL OR major = ''");
                    }
                    courseCheck.close();
                }

                // Get count per major for debugging
                Cursor majorStats = db.rawQuery(
                        "SELECT major, COUNT(*) FROM " + TABLE_COURSES + " GROUP BY major",
                        null
                );

                if (majorStats != null) {
                    while (majorStats.moveToNext()) {
                        String major = majorStats.getString(0);
                        int count = majorStats.getInt(1);
                        Log.d("DatabaseManager", "Major '" + major + "' has " + count + " courses");
                    }
                    majorStats.close();
                }

            } catch (Exception e) {
                Log.e("DatabaseManager", "Error ensuring courses table columns", e);
            }
            // Don't close the database here
        }
    }

    private SQLiteDatabase getWritableDatabase() {
        synchronized (dbLock) {
            try {
                if (writableDb == null || !writableDb.isOpen()) {
                    writableDb = dbHelper.getWritableDatabase();
                }
                return writableDb;
            } catch (Exception e) {
                Log.e("DatabaseManager", "Error getting writable database", e);
                // Try to recover by recreating the database connection
                try {
                    closeInternal(); // Close any existing connections
                    dbHelper = new DatabaseHelper(context); // Create new helper
                    writableDb = dbHelper.getWritableDatabase(); // Get fresh connection
                    return writableDb;
                } catch (Exception ex) {
                    Log.e("DatabaseManager", "Failed to recover writable database", ex);
                    throw ex; // Propagate the exception after logging
                }
            }
        }
    }


    public Cursor searchCourses(String query) {
        synchronized (dbLock) {
            SQLiteDatabase db = getReadableDatabase();

            try {
                return db.query(
                        TABLE_COURSES,
                        new String[]{"id as _id", "course", "reference", "credits", "description", "session", "start_date", "end_date", "instructor"},
                        "course LIKE ? OR reference LIKE ? OR description LIKE ?",
                        new String[]{"%" + query + "%", "%" + query + "%", "%" + query + "%"},
                        null, null, "course ASC");
            } catch (Exception e) {
                Log.e("DatabaseManager", "Error searching courses", e);

                // Try with fewer columns if that fails
                return db.query(
                        TABLE_COURSES,
                        new String[]{"id as _id", "course", "reference", "description"},
                        "course LIKE ? OR reference LIKE ? OR description LIKE ?",
                        new String[]{"%" + query + "%", "%" + query + "%", "%" + query + "%"},
                        null, null, "course ASC");
            }
        }
    }

    public Cursor getCourseById(int courseId) {
        synchronized (dbLock) {
            SQLiteDatabase db = getReadableDatabase();

            return db.query(
                    TABLE_COURSES,
                    null,
                    "id = ?",
                    new String[]{String.valueOf(courseId)},
                    null, null, null);
        }
    }

    public boolean addCourse(String course, String reference, String credits, String description) {
        synchronized (dbLock) {
            SQLiteDatabase db = getWritableDatabase();

            ContentValues values = new ContentValues();

            values.put("course", course);
            values.put("reference", reference);
            values.put("credits", credits);
            values.put("description", description);

            // Determine a suitable major based on course name
            String major = determineMajorFromCourse(course, reference, description);
            values.put("major", major);

            long result = db.insert(TABLE_COURSES, null, values);
            // Don't close the database here
            return result != -1;
        }
    }

    // Helper method to determine major from course information
    private String determineMajorFromCourse(String course, String reference, String description) {
        String lowerCourse = course.toLowerCase();
        String lowerRef = reference.toLowerCase();
        String lowerDesc = description != null ? description.toLowerCase() : "";

        // Simple major detection
        if (lowerCourse.contains("art") || lowerCourse.contains("design") ||
                lowerCourse.contains("music") || lowerRef.startsWith("art")) {
            return "Arts";
        } else if (lowerCourse.contains("bio") || lowerCourse.contains("chem") ||
                lowerCourse.contains("phys") || lowerCourse.contains("math") ||
                lowerCourse.contains("computer") || lowerRef.startsWith("sci")) {
            return "STEM";
        } else if (lowerCourse.contains("business") || lowerCourse.contains("account") ||
                lowerCourse.contains("finance") || lowerCourse.contains("market")) {
            return "Business";
        } else if (lowerCourse.contains("edu") || lowerCourse.contains("teach")) {
            return "Education";
        } else if (lowerCourse.contains("health") || lowerCourse.contains("nursing") ||
                lowerCourse.contains("med")) {
            return "Health Science";
        } else {
            return "none"; // Default value
        }
    }

    public boolean updateCourse(int courseId, String course, String reference, String credits, String description) {
        synchronized (dbLock) {
            SQLiteDatabase db = getWritableDatabase();

            ContentValues values = new ContentValues();
            values.put("course", course);
            values.put("reference", reference);
            values.put("credits", credits);
            values.put("description", description);
            // Update other fields as needed

            int result = db.update(
                    TABLE_COURSES,
                    values,
                    "id = ?",
                    new String[]{String.valueOf(courseId)});

            // Don't close the database here
            return result > 0;
        }
    }

    public boolean deleteCourse(int courseId) {
        synchronized (dbLock) {
            SQLiteDatabase db = getWritableDatabase();

            // Delete course-program relationships first
            db.delete(
                    DatabaseHelper.TABLE_COURSE_PROGRAMS,
                    "course_id = ?",
                    new String[]{String.valueOf(courseId)});

            // Then delete the course
            int result = db.delete(
                    TABLE_COURSES,
                    "id = ?",
                    new String[]{String.valueOf(courseId)});

            // Don't close the database here
            return result > 0;
        }
    }

    // Academic Programs operations
    public boolean importAcademicProgramsFromCSV(String[] csvLines) {
        SQLiteDatabase db = null;
        boolean success = true;
        int insertCount = 0;

        try {
            synchronized (dbLock) {
                db = getWritableDatabase();
                db.beginTransaction();

                // Check if programs already exist
                Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_ACADEMIC_PROGRAMS, null);
                int programCount = 0;
                if (cursor.moveToFirst()) {
                    programCount = cursor.getInt(0);
                }
                cursor.close();

                // Only proceed with import if no programs exist
                if (programCount == 0) {
                    // If we only have one line, it might be comma-separated
                    if (csvLines.length == 1) {
                        String[] programs = csvLines[0].split(",");
                        for (String program : programs) {
                            program = program.trim();
                            if (!TextUtils.isEmpty(program)) {
                                ContentValues contentValues = new ContentValues();
                                contentValues.put("program_name", program);
                                // Use default values for other columns
                                contentValues.put("pathway", "General");  // Default pathway
                                contentValues.put("description", program + " program");  // Default description

                                long result = db.insert(DatabaseHelper.TABLE_ACADEMIC_PROGRAMS, null, contentValues);
                                if (result != -1) {
                                    insertCount++;
                                }
                            }
                        }
                    } else {
                        // Each line is a program name
                        for (String line : csvLines) {
                            line = line.trim();
                            if (!TextUtils.isEmpty(line)) {
                                ContentValues contentValues = new ContentValues();
                                contentValues.put("program_name", line);
                                // Use default values for other columns
                                contentValues.put("pathway", "General");  // Default pathway
                                contentValues.put("description", line + " program");  // Default description

                                long result = db.insert(DatabaseHelper.TABLE_ACADEMIC_PROGRAMS, null, contentValues);
                                if (result != -1) {
                                    insertCount++;
                                }
                            }
                        }
                    }
                }

                db.setTransactionSuccessful();
                Log.i("CSV Import", "Successfully imported " + insertCount + " academic programs");
            }
        } catch (Exception e) {
            success = false;
            Log.e("CSV Import", "Error importing academic programs", e);
        } finally {
            if (db != null && db.inTransaction()) {
                try {
                    db.endTransaction();
                } catch (Exception e) {
                    Log.e("CSV Import", "Error ending transaction", e);
                }
            }
        }

        return success;
    }

    // Get all courses for a specific program
    public Cursor getCoursesForProgram(int programId) {
        synchronized (dbLock) {
            SQLiteDatabase db = getReadableDatabase();

            try {
                return db.rawQuery(
                        "SELECT c.id as _id, c.course, c.reference, c.credits, c.description, " +
                                "c.session, c.start_date, c.end_date, c.instructor " +
                                "FROM " + TABLE_COURSES + " c " +
                                "JOIN " + DatabaseHelper.TABLE_COURSE_PROGRAMS + " cp ON c.id = cp.course_id " +
                                "WHERE cp.program_id = ? " +
                                "ORDER BY c.course",
                        new String[]{String.valueOf(programId)});
            } catch (Exception e) {
                Log.e("DatabaseManager", "Error getting courses for program", e);
                // Return empty cursor
                return db.rawQuery(
                        "SELECT id as _id, course, reference, credits, description, " +
                                "session, start_date, end_date, instructor " +
                                "FROM " + TABLE_COURSES + " LIMIT 0",
                        null);
            }
        }
    }

    // Search courses within a specific program
    public Cursor searchCoursesInProgram(int programId, String query) {
        synchronized (dbLock) {
            SQLiteDatabase db = getReadableDatabase();

            try {
                return db.rawQuery(
                        "SELECT c.id as _id, c.course, c.reference, c.credits, c.description, " +
                                "c.session, c.start_date, c.end_date, c.instructor " +
                                "FROM " + TABLE_COURSES + " c " +
                                "JOIN " + DatabaseHelper.TABLE_COURSE_PROGRAMS + " cp ON c.id = cp.course_id " +
                                "WHERE cp.program_id = ? AND " +
                                "(c.course LIKE ? OR c.reference LIKE ? OR c.description LIKE ?) " +
                                "ORDER BY c.course",
                        new String[]{
                                String.valueOf(programId),
                                "%" + query + "%",
                                "%" + query + "%",
                                "%" + query + "%"
                        });
            } catch (Exception e) {
                Log.e("DatabaseManager", "Error searching courses in program", e);
                // Return empty cursor
                return db.rawQuery(
                        "SELECT id as _id, course, reference, credits, description, " +
                                "session, start_date, end_date, instructor " +
                                "FROM " + TABLE_COURSES + " LIMIT 0",
                        null);
            }
        }
    }

    public int getAcademicProgramCount() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        int count = 0;

        try {
            Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_ACADEMIC_PROGRAMS, null);
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
            cursor.close();
        } catch (Exception e) {
            Log.e("DatabaseManager", "Error getting academic program count", e);
            // Table might not exist
            ensureAcademicProgramsTableExists();
        }

        db.close();
        return count;
    }
    public Cursor getAllAcademicPrograms() {
        SQLiteDatabase db = getReadableDatabase();
        return db.query(
                DatabaseHelper.TABLE_ACADEMIC_PROGRAMS,
                new String[]{"id as _id", "program_name", "pathway", "description"},
                null, null, null, null, "program_name ASC");
    }

    // Associate a course with an academic program
    public boolean addCourseToProgram(int courseId, int programId) {
        synchronized (dbLock) {
            SQLiteDatabase db = getWritableDatabase();

            ContentValues values = new ContentValues();
            values.put("course_id", courseId);
            values.put("program_id", programId);

            try {
                long result = db.insert(DatabaseHelper.TABLE_COURSE_PROGRAMS, null, values);
                return result != -1;
            } catch (Exception e) {
                Log.e("DatabaseManager", "Error adding course to program", e);
                return false;
            }
        }
    }

    // REMOVE COURSE FROM PROGRAM
    public boolean removeCourseFromProgram(int courseId, int programId) {
        synchronized (dbLock) {
            SQLiteDatabase db = getWritableDatabase();

            try {
                int result = db.delete(
                        DatabaseHelper.TABLE_COURSE_PROGRAMS,
                        "course_id = ? AND program_id = ?",
                        new String[]{String.valueOf(courseId), String.valueOf(programId)}
                );
                return result > 0;
            } catch (Exception e) {
                Log.e("DatabaseManager", "Error removing course from program", e);
                return false;
            }
        }
    }

    // Get all programs for a specific course
    public Cursor getProgramsForCourse(int courseId) {
        synchronized (dbLock) {
            SQLiteDatabase db = getReadableDatabase();

            return db.rawQuery(
                    "SELECT p.id as _id, p.program_name " +
                            "FROM " + DatabaseHelper.TABLE_ACADEMIC_PROGRAMS + " p " +
                            "JOIN " + DatabaseHelper.TABLE_COURSE_PROGRAMS + " cp ON p.id = cp.program_id " +
                            "WHERE cp.course_id = ? " +
                            "ORDER BY p.program_name",
                    new String[]{String.valueOf(courseId)}
            );
        }
    }

    // Check if a course is in a program
    public boolean isCourseInProgram(int courseId, int programId) {
        synchronized (dbLock) {
            SQLiteDatabase db = getReadableDatabase();

            Cursor cursor = db.rawQuery(
                    "SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_COURSE_PROGRAMS +
                            " WHERE course_id = ? AND program_id = ?",
                    new String[]{String.valueOf(courseId), String.valueOf(programId)}
            );

            boolean result = false;
            if (cursor != null && cursor.moveToFirst()) {
                result = cursor.getInt(0) > 0;
                cursor.close();
            }

            return result;
        }
    }

    public void ensureAcademicProgramsTableExists() {
        SQLiteDatabase db = null;
        try {
            db = dbHelper.getWritableDatabase();

            // Check if table exists
            Cursor cursor = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                    new String[]{DatabaseHelper.TABLE_ACADEMIC_PROGRAMS});

            boolean tableExists = cursor.getCount() > 0;
            cursor.close();

            if (!tableExists) {
                // Create academic programs table
                String CREATE_ACADEMIC_PROGRAMS_TABLE = "CREATE TABLE " + DatabaseHelper.TABLE_ACADEMIC_PROGRAMS + " ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "program_name TEXT UNIQUE, "
                        + "pathway TEXT, "
                        + "description TEXT)";
                db.execSQL(CREATE_ACADEMIC_PROGRAMS_TABLE);

                // Create course_programs junction table
                String CREATE_COURSE_PROGRAMS_TABLE = "CREATE TABLE " + DatabaseHelper.TABLE_COURSE_PROGRAMS + " ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "course_id INTEGER, "
                        + "program_id INTEGER, "
                        + "FOREIGN KEY(course_id) REFERENCES " + TABLE_COURSES + "(id), "
                        + "FOREIGN KEY(program_id) REFERENCES " + DatabaseHelper.TABLE_ACADEMIC_PROGRAMS + "(id), "
                        + "UNIQUE(course_id, program_id))";
                db.execSQL(CREATE_COURSE_PROGRAMS_TABLE);
            }
        } catch (SQLiteDatabaseLockedException e) {
            Log.e("DatabaseManager", "Database locked: " + e.getMessage());
            // Don't throw the exception, just log it
        } catch (Exception e) {
            Log.e("DatabaseManager", "Error ensuring academic programs table: " + e.getMessage());
        }
    }


    // CAREER OPERATIONS

    public Cursor getAllCareers() {
        SQLiteDatabase db = getReadableDatabase();

        return db.rawQuery(
                "SELECT id, occupation_title, occupation_code, employment_2023, " +
                        "employment_percent_change, median_annual_wage, education_work_experience " +
                        "FROM careers ORDER BY occupation_title",
                null);
    }

    public Cursor searchCareers(String query) {
        SQLiteDatabase db = getReadableDatabase();

        return db.rawQuery(
                "SELECT id, occupation_title, occupation_code, employment_2023, " +
                        "employment_percent_change, median_annual_wage, education_work_experience " +
                        "FROM careers WHERE occupation_title LIKE ? OR occupation_code LIKE ?",
                new String[]{"%" + query + "%", "%" + query + "%"});
    }

    public boolean addCareer(String occupationTitle, String occupationCode,
                             int employment2023, float employmentChange,
                             float medianWage, String education) {
        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("occupation_title", occupationTitle);
        values.put("occupation_code", occupationCode);
        values.put("employment_2023", employment2023);
        values.put("employment_percent_change", employmentChange);
        values.put("median_annual_wage", medianWage);
        values.put("education_work_experience", education);

        long result = db.insert("careers", null, values);
        db.close();

        return result != -1;
    }

    // CAREER by title
    public Cursor getCareerDetailsByTitle(String occupationTitle) {
        SQLiteDatabase db = getReadableDatabase();

        Log.d("DatabaseManager", "Searching for career title: " + occupationTitle);

        // Remove any trailing details
        occupationTitle = occupationTitle.split("\t")[0].trim();

        Cursor cursor = db.rawQuery(
                "SELECT * FROM careers WHERE occupation_title = ?",
                new String[]{occupationTitle}
        );

        Log.d("DatabaseManager", "Exact match cursor count: " + cursor.getCount());

        // If no exact match, try partial match
        if (cursor.getCount() == 0) {
            cursor.close();

            cursor = db.rawQuery(
                    "SELECT * FROM careers WHERE occupation_title LIKE ?",
                    new String[]{"%" + occupationTitle + "%"}
            );

            Log.d("DatabaseManager", "Partial match cursor count: " + cursor.getCount());
        }

        return cursor;
    }

    // CAREER-COURSE RELATIONSHIP

    // linking occupations with related courses
    public void createCareerCoursesTable() {
        SQLiteDatabase db = getWritableDatabase();

        // Check if the table already exists
        Cursor cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='career_courses'",
                null);

        boolean tableExists = cursor.getCount() > 0;
        cursor.close();

        if (!tableExists) {
            String CREATE_CAREER_COURSES_TABLE = "CREATE TABLE career_courses ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "career_id INTEGER, "
                    + "course_id INTEGER, "
                    + "relevance INTEGER, "
                    + "UNIQUE(career_id, course_id))";
            db.execSQL(CREATE_CAREER_COURSES_TABLE);
        }

        db.close();
    }

    public boolean addRecommendedCourse(int careerId, int courseId, int relevance) {
        // First ensure the table exists
        createCareerCoursesTable();

        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("career_id", careerId);
        values.put("course_id", courseId);
        values.put("relevance", relevance);

        long result = db.insert("career_courses", null, values);
        db.close();

        return result != -1;
    }

    public boolean removeRecommendedCourse(int careerId, int courseId) {
        SQLiteDatabase db = getWritableDatabase();

        int result = db.delete("career_courses",
                "career_id = ? AND course_id = ?",
                new String[]{String.valueOf(careerId), String.valueOf(courseId)});

        db.close();
        return result > 0;
    }

    public Cursor getRecommendedCourses(int careerId) {
        SQLiteDatabase db = getReadableDatabase();

        return db.rawQuery(
                "SELECT c.id, c.title, c.description, cc.relevance " +
                        "FROM courses c " +
                        "JOIN career_courses cc ON c.id = cc.course_id " +
                        "WHERE cc.career_id = ? " +
                        "ORDER BY cc.relevance DESC",
                new String[]{String.valueOf(careerId)});
    }

    public int getRecommendationCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM career_courses", null);
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        db.close();
        return count;
    }
    // CSV IMPORT METHODS

    public boolean importCareersFromCSV(String[] csvLines) {
        SQLiteDatabase db = getWritableDatabase();
        boolean success = true;
        int insertCount = 0;

        try {
            db.beginTransaction();

            for (int i = 1; i < csvLines.length; i++) { // Skip header row
                // Use tab as delimiter and trim each value
                String[] values = csvLines[i].trim().split("\t");

                if (values.length >= 6) {
                    ContentValues contentValues = new ContentValues();
                    contentValues.put("occupation_title", values[0].trim());
                    contentValues.put("occupation_code", values[1].trim());

                    try {
                        contentValues.put("employment_2023",
                                Float.parseFloat(values[2].trim()));
                    } catch (Exception e) {
                        contentValues.put("employment_2023", 0);
                    }

                    try {
                        contentValues.put("employment_percent_change",
                                Float.parseFloat(values[3].trim()));
                    } catch (Exception e) {
                        contentValues.put("employment_percent_change", 0.0f);
                    }

                    try {
                        contentValues.put("median_annual_wage",
                                Float.parseFloat(values[4].trim()));
                    } catch (Exception e) {
                        contentValues.put("median_annual_wage", 0.0f);
                    }

                    contentValues.put("education_work_experience", values[5].trim());

                    long result = db.insert("careers", null, contentValues);
                    if (result != -1) {
                        insertCount++;
                    }
                }
            }

            db.setTransactionSuccessful();
        } catch (Exception e) {
            success = false;
            e.printStackTrace();
        } finally {
            db.endTransaction();
            db.close();
        }

        return success;
    }

    public void clearCareersTable() {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("DELETE FROM " + TABLE_CAREERS);
        db.close();
    }

    public int getCareerCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_CAREERS, null);
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        db.close();
        return count;
    }

    public static class Recommendation {
        public int id;
        public String recommendationInfo;
        public String relevanceInfo;

        public Recommendation(int id, String recommendationInfo, String relevanceInfo) {
            this.id = id;
            this.recommendationInfo = recommendationInfo;
            this.relevanceInfo = relevanceInfo;
        }
    }

    public List<Recommendation> getAllRecommendationsSafe() {
        List<Recommendation> list = new ArrayList<>();
        createCareerCoursesTable(); // Ensure table exists

        synchronized (dbLock) {
            SQLiteDatabase db = getReadableDatabase();
            Cursor cursor = null;
            Cursor columnCheck = null;
            Cursor tableCheck = null;

            try {
                // Check if the table exists
                tableCheck = db.rawQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name='career_courses'",
                        null);
                boolean tableExists = tableCheck.getCount() > 0;

                if (!tableExists) {
                    Log.w("DatabaseManager", "career_courses table does not exist");
                    return list;
                }

                // Check column names in the courses table
                columnCheck = db.rawQuery("PRAGMA table_info(" + TABLE_COURSES + ")", null);
                String courseColumnName = "course"; // default

                if (columnCheck.moveToFirst()) {
                    do {
                        int nameIndex = columnCheck.getColumnIndex("name");
                        if (nameIndex != -1) {
                            String colName = columnCheck.getString(nameIndex);
                            if ("title".equals(colName)) {
                                courseColumnName = "title";
                                break;
                            } else if ("course".equals(colName)) {
                                courseColumnName = "course";
                                break;
                            }
                        }
                    } while (columnCheck.moveToNext());
                }

                // Construct and run the actual query
                cursor = db.rawQuery(
                        "SELECT cc.id AS _id, " +
                                "ca.occupation_title || ' → ' || co." + courseColumnName + " AS recommendation_info, " +
                                "'Relevance: ' || cc.relevance || '/10' AS relevance_info " +
                                "FROM career_courses cc " +
                                "JOIN " + DatabaseHelper.TABLE_CAREERS + " ca ON cc.career_id = ca.id " +
                                "JOIN " + TABLE_COURSES + " co ON cc.course_id = co.id " +
                                "ORDER BY ca.occupation_title, cc.relevance DESC", null);

                if (cursor.moveToFirst()) {
                    do {
                        int id = cursor.getInt(cursor.getColumnIndexOrThrow("_id"));
                        String recInfo = cursor.getString(cursor.getColumnIndexOrThrow("recommendation_info"));
                        String relInfo = cursor.getString(cursor.getColumnIndexOrThrow("relevance_info"));

                        list.add(new Recommendation(id, recInfo, relInfo));
                    } while (cursor.moveToNext());
                }

            } catch (Exception e) {
                Log.e("DatabaseManager", "Error fetching recommendations", e);
            } finally {
                if (cursor != null && !cursor.isClosed()) cursor.close();
                if (tableCheck != null && !tableCheck.isClosed()) tableCheck.close();
                if (columnCheck != null && !columnCheck.isClosed()) columnCheck.close();
            }
        }

        return list;
    }

    public Cursor getAllRecommendations() {
        createCareerCoursesTable(); // Ensure the table exists
        synchronized (dbLock) {
            try {
                SQLiteDatabase db = getReadableDatabase();

                // First check if the career_courses table exists
                Cursor tableCheck = db.rawQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name='career_courses'",
                        null);
                boolean tableExists = tableCheck.getCount() > 0;
                tableCheck.close();

                if (!tableExists) {
                    Log.w("DatabaseManager", "career_courses table does not exist");
                    return null;
                }

                // Now check column names in the courses table
                Cursor columnCheck = db.rawQuery("PRAGMA table_info(" + TABLE_COURSES + ")", null);
                String courseColumnName = "course"; // Default column name

                if (columnCheck != null) {
                    if (columnCheck.moveToFirst()) {
                        do {
                            int nameIndex = columnCheck.getColumnIndex("name");
                            if (nameIndex != -1) {
                                String colName = columnCheck.getString(nameIndex);
                                if ("title".equals(colName)) {
                                    courseColumnName = "title";
                                    break;
                                } else if ("course".equals(colName)) {
                                    courseColumnName = "course";
                                    break;
                                }
                            }
                        } while (columnCheck.moveToNext());
                    }
                    columnCheck.close();
                }

                // Now construct query with the correct column name
                return db.rawQuery(
                        "SELECT cc.id AS _id, " +
                                "ca.occupation_title || ' → ' || co." + courseColumnName + " AS recommendation_info, " +
                                "'Relevance: ' || cc.relevance || '/10' AS relevance_info " +
                                "FROM career_courses cc " +
                                "JOIN " + DatabaseHelper.TABLE_CAREERS + " ca ON cc.career_id = ca.id " +
                                "JOIN " + TABLE_COURSES + " co ON cc.course_id = co.id " +
                                "ORDER BY ca.occupation_title, cc.relevance DESC", null);
            } catch (Exception e) {
                Log.e("DatabaseManager", "Error getting all recommendations", e);
                return null;
            }
        }
    }
    public static class CareerIdPair {
        public int id;
        public String title;

        public CareerIdPair(int id, String title) {
            this.id = id;
            this.title = title;
        }
    }

    public List<CareerIdPair> getCareerIdTitlePairs() {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        List<CareerIdPair> pairs = new ArrayList<>();

        try {
            db = getReadableDatabase();
            cursor = db.rawQuery("SELECT id, occupation_title FROM careers", null);

            // Safely get column indices with validation
            int idColumnIndex = cursor.getColumnIndex("id");
            int titleColumnIndex = cursor.getColumnIndex("occupation_title");

            // Validate column indices
            if (idColumnIndex < 0 || titleColumnIndex < 0) {
                Log.e("DatabaseManager", "Invalid column indices in getCareerIdTitlePairs()");
                return pairs;
            }

            while (cursor.moveToNext()) {
                try {
                    // Safely retrieve values
                    int id = cursor.getInt(idColumnIndex);
                    String title = cursor.getString(titleColumnIndex);

                    // Only add if both id and title are valid
                    if (title != null && !title.trim().isEmpty()) {
                        pairs.add(new CareerIdPair(id, title));
                    }
                } catch (Exception e) {
                    Log.e("DatabaseManager", "Error processing individual career pair", e);
                }
            }
        } catch (Exception e) {
            Log.e("DatabaseManager", "Error retrieving career ID pairs", e);
        } finally {
            // Ensure cursor and database are closed
            if (cursor != null) {
                cursor.close();
            }
            if (db != null) {
                db.close();
            }
        }

        // If no careers found, add a default "All Careers" entry
        if (pairs.isEmpty()) {
            pairs.add(new CareerIdPair(-1, "All Careers"));
        }

        return pairs;
    }
    public boolean courseExists(int courseId) {
        SQLiteDatabase db = getReadableDatabase();

        Cursor cursor = db.rawQuery("SELECT id FROM courses WHERE id = ?",
                new String[]{String.valueOf(courseId)});

        boolean exists = cursor.getCount() > 0;
        cursor.close();
        db.close();

        return exists;
    }

    public boolean updateOrAddRecommendedCourse(int careerId, int courseId, int relevance) {
        createCareerCoursesTable(); // Ensure the table exists
        SQLiteDatabase db = getWritableDatabase();

        // Check if this recommendation already exists
        Cursor cursor = db.rawQuery(
                "SELECT id FROM career_courses WHERE career_id = ? AND course_id = ?",
                new String[]{String.valueOf(careerId), String.valueOf(courseId)});

        boolean exists = cursor.getCount() > 0;
        cursor.close();

        ContentValues values = new ContentValues();
        values.put("career_id", careerId);
        values.put("course_id", courseId);
        values.put("relevance", relevance);

        boolean success;

        if (exists) {
            // Update existing record
            int result = db.update("career_courses", values,
                    "career_id = ? AND course_id = ?",
                    new String[]{String.valueOf(careerId), String.valueOf(courseId)});
            success = (result > 0);
        } else {
            // Insert new record
            long result = db.insert("career_courses", null, values);
            success = (result != -1);
        }

        db.close();
        return success;

    }

    public boolean deleteRecommendation(int recommendationId) {
        SQLiteDatabase db = getWritableDatabase();

        int result = db.delete(
                "career_courses",
                "id = ?",
                new String[]{String.valueOf(recommendationId)});

        db.close();
        return result > 0;
    }

    // ADMINISTRATION OPERATIONS
    public static final String TABLE_ADMINISTRATORS = "administrators";

    // Create administrators table
    public void createAdministratorsTable() {
        SQLiteDatabase db = getWritableDatabase();

        // Check if table already exists
        Cursor cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                new String[]{TABLE_ADMINISTRATORS});

        boolean tableExists = cursor.getCount() > 0;
        cursor.close();

        if (!tableExists) {
            String CREATE_ADMINISTRATORS_TABLE = "CREATE TABLE " + TABLE_ADMINISTRATORS + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "name TEXT, "
                    + "title TEXT, "
                    + "email TEXT, "
                    + "phone TEXT, "
                    + "department TEXT)";
            db.execSQL(CREATE_ADMINISTRATORS_TABLE);
        }

        db.close();
    }

    // Import administrators from CSV
    public boolean importAdministratorsFromCSV(String[] lines) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();

        try {
            // Check if administrators already exist
            Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_ADMINISTRATORS, null);
            int adminCount = 0;
            if (cursor.moveToFirst()) {
                adminCount = cursor.getInt(0);
            }
            cursor.close();

            // Only import if we have no administrators
            if (adminCount == 0) {
                // Skip header row (first line)
                for (int i = 1; i < lines.length; i++) {
                    // Split the line carefully to handle names with spaces
                    String[] values = lines[i].split("\t");

                    // Ensure we have enough values
                    if (values.length >= 5) {
                        ContentValues cv = new ContentValues();
                        cv.put("name", values[0].trim()); // Name (with potential comma)
                        cv.put("title", values[1].trim()); // Title
                        cv.put("email", values[2].trim()); // Email
                        cv.put("phone", values[3].trim()); // Phone
                        cv.put("department", values[4].trim()); // Department

                        long result = db.insert(TABLE_ADMINISTRATORS, null, cv);

                        if (result == -1) {
                            Log.e("CSV Import", "Failed to insert line: " + lines[i]);
                        }
                    } else {
                        Log.e("CSV Import", "Insufficient values in line: " + lines[i]);
                    }
                }
            }

            db.setTransactionSuccessful();
            return true;
        } catch (Exception e) {
            Log.e("CSV Import", "Error importing administrators", e);
            return false;
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    public int getAdministratorCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_ADMINISTRATORS, null);
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        db.close();
        return count;
    }

    // Method to get all administrator names for dropdown
    public Cursor getAllAdministrators() {
        SQLiteDatabase db = getReadableDatabase();

        return db.query(DatabaseHelper.TABLE_ADMINISTRATORS,
                new String[]{"id as _id", "name", "title", "department", "email", "phone"},
                null,
                null,
                null,
                null,
                "name ASC");
    }



    // Get administrator by ID
    public Cursor getAdministratorById(int adminId) {
        SQLiteDatabase db = getReadableDatabase();

        return db.rawQuery(
                "SELECT id as _id, name, title, email, phone, department FROM " + TABLE_ADMINISTRATORS +
                        " WHERE id = ?",
                new String[]{String.valueOf(adminId)});
    }

    public static final String TABLE_AVAILABILITY = "availability";
    // Get all time slots for a specific admin and date
    public Cursor getAdminTimeSlots(int adminId, String date) {
        createAvailabilityTable(); // Ensure table exists
        SQLiteDatabase db = getReadableDatabase();

        return db.rawQuery(
                "SELECT id as _id, time_slot, " +
                        "CASE WHEN is_available = 1 THEN 'Available' ELSE 'Booked' END as status " +
                        "FROM " + TABLE_AVAILABILITY +
                        " WHERE admin_id = ? AND date = ? " +
                        "ORDER BY time_slot",
                new String[]{String.valueOf(adminId), date});
    }
    private void createAvailabilityTable() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Check if table already exists
        Cursor cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                new String[]{TABLE_AVAILABILITY});

        boolean tableExists = cursor.getCount() > 0;
        cursor.close();

        if (!tableExists) {
            String CREATE_AVAILABILITY_TABLE = "CREATE TABLE " + TABLE_AVAILABILITY + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "admin_id INTEGER, "
                    + "date TEXT, "
                    + "time_slot TEXT, "
                    + "is_available INTEGER DEFAULT 1, "
                    + "UNIQUE(admin_id, date, time_slot))";
            db.execSQL(CREATE_AVAILABILITY_TABLE);
        }

        db.close();
    }

        // Get list of existing time slots for an admin on a specific date
    public List<String> getExistingTimeSlots(int adminId, String date) {
        createAvailabilityTable(); // Ensure table exists
        SQLiteDatabase db = getReadableDatabase();
        List<String> timeSlots = new ArrayList<>();

        Cursor cursor = db.rawQuery(
                "SELECT time_slot FROM " + TABLE_AVAILABILITY +
                        " WHERE admin_id = ? AND date = ?",
                new String[]{String.valueOf(adminId), date});

        if (cursor != null) {
            while (cursor.moveToNext()) {
                timeSlots.add(cursor.getString(0));
            }
            cursor.close();
        }

        db.close();
        return timeSlots;
    }

    // Delete a time slot
    public boolean deleteTimeSlot(int timeSlotId) {
        SQLiteDatabase db = getWritableDatabase();

        // First check if it's not already booked
        Cursor cursor = db.rawQuery(
                "SELECT is_available FROM " + TABLE_AVAILABILITY + " WHERE id = ?",
                new String[]{String.valueOf(timeSlotId)});

        boolean canDelete = false;
        if (cursor != null && cursor.moveToFirst()) {
            canDelete = cursor.getInt(0) == 1; // Can only delete if available
            cursor.close();
        }

        if (!canDelete) {
            db.close();
            return false; // Can't delete booked slots
        }

        int result = db.delete(
                TABLE_AVAILABILITY,
                "id = ?",
                new String[]{String.valueOf(timeSlotId)});

        db.close();
        return result > 0;
    }

    /**
     * Add a new availability time slot for an administrator
     * @param adminId The administrator ID
     * @param date The date (in format YYYY-MM-DD)
     * @param timeSlot The time slot (e.g., "9:00 AM")
     * @return true if successful, false otherwise
     */

    public boolean addAdminAvailability(int adminId, String date, String timeSlot) {
        createAvailabilityTable(); // Ensure table exists
        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("admin_id", adminId);
        values.put("date", date);
        values.put("time_slot", timeSlot);
        values.put("is_available", 1); // 1 = available

        long result = db.insert(TABLE_AVAILABILITY, null, values);
        db.close();

        return result != -1;
    }


    // Get available time slots for administrator on specific date
    public Cursor getAvailableTimeSlots(int adminId, String date) {
        createAvailabilityTable(); // Ensure table exists
        SQLiteDatabase db = getReadableDatabase();

        return db.rawQuery(
                "SELECT id as _id, time_slot FROM " + TABLE_AVAILABILITY +
                        " WHERE admin_id = ? AND date = ? AND is_available = 1" +
                        " ORDER BY time_slot",
                new String[]{String.valueOf(adminId), date});
    }


    // BOOKING OPERATION
    public static final String TABLE_APPOINTMENTS = "appointments";
    private void createAppointmentsTable() {
        SQLiteDatabase db = getWritableDatabase();
        String createTableSQL = "CREATE TABLE IF NOT EXISTS " + TABLE_APPOINTMENTS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "student_username TEXT NOT NULL, " +
                "admin_id INTEGER NOT NULL, " +
                "date TEXT NOT NULL, " +
                "time_slot TEXT NOT NULL, " +
                "reason TEXT" +
                ")";
        db.execSQL(createTableSQL);
        db.close();
    }

    public boolean bookAppointment(String studentUsername, int adminId, String date, String timeSlot, String reason) {
        createAppointmentsTable(); // Ensure table exists
        SQLiteDatabase db = getWritableDatabase();
        boolean success = false;

        try {
            db.beginTransaction();

            // First, update availability
            ContentValues availValues = new ContentValues();
            availValues.put("is_available", 0); // Mark as unavailable

            int updateResult = db.update(
                    TABLE_AVAILABILITY,
                    availValues,
                    "admin_id = ? AND date = ? AND time_slot = ?",
                    new String[]{String.valueOf(adminId), date, timeSlot});

            if (updateResult > 0) {
                // Then create appointment record
                ContentValues apptValues = new ContentValues();
                apptValues.put("student_username", studentUsername);
                apptValues.put("admin_id", adminId);
                apptValues.put("date", date);
                apptValues.put("time_slot", timeSlot);
                apptValues.put("reason", reason);

                long insertResult = db.insert(TABLE_APPOINTMENTS, null, apptValues);

                if (insertResult != -1) {
                    success = true;
                }
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }

        return success;
    }


    public void onLowMemory() {
        synchronized (dbLock) {
            // Close readable database if it's open
            try {
                if (readableDb != null && readableDb.isOpen()) {
                    readableDb.close();
                    readableDb = null;
                }
            } catch (Exception e) {
                Log.e("DatabaseManager", "Error closing readable database in low memory", e);
            }

            // We can keep the writable database if needed, or close it as well
            // depending on your application's needs

            // Log the event
            Log.i("DatabaseManager", "onLowMemory called - released database resources");
        }
    }



}


