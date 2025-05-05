package com.example.experiment;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CourseDetailsActivity extends Activity {
    private static final String TAG = "CourseDetails";

    private TextView tvCourseTitle;
    private TextView tvCourseReference;
    private TextView tvCourseDescription;
    private TextView tvCredits;
    private TextView tvSession;
    private TextView tvStartDate;
    private TextView tvEndDate;
    private TextView tvInstructor;
    private Button btnEnroll;
    private Button btnSave;
    private Button btnBack;

    private DatabaseManager dbManager;
    private ExecutorService executorService;
    private Handler mainThreadHandler;

    private int courseId;
    private String courseName;
    private String courseReference;
    private boolean isCourseSaved = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_course_details);

        // Initialize thread management
        executorService = Executors.newSingleThreadExecutor();
        mainThreadHandler = new Handler(Looper.getMainLooper());

        // Initialize database manager
        dbManager = DatabaseManager.getInstance(this);

        // Initialize UI elements
        initViews();

        // Get course ID from intent
        courseId = getIntent().getIntExtra("course_id", -1);
        if (courseId == -1) {
            Toast.makeText(this, "Error: Course not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Check if course is already saved
        checkIfCourseSaved();

        // Load course data
        loadCourseData(courseId);

        // Set button click listeners
        setupButtonListeners();
    }

    private void initViews() {
        tvCourseTitle = findViewById(R.id.tvCourseTitle);
        tvCourseReference = findViewById(R.id.tvCourseReference);
        tvCourseDescription = findViewById(R.id.tvCourseDescription);
        tvCredits = findViewById(R.id.tvCredits);
        tvSession = findViewById(R.id.tvSession);
        tvStartDate = findViewById(R.id.tvStartDate);
        tvEndDate = findViewById(R.id.tvEndDate);
        tvInstructor = findViewById(R.id.tvInstructor);
        btnEnroll = findViewById(R.id.btnEnroll);
        btnSave = findViewById(R.id.btnSave);
        btnBack = findViewById(R.id.btnBack);
    }

    private void checkIfCourseSaved() {
        // Get username
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String username = prefs.getString("username", "");

        // Get saved courses
        SharedPreferences savedCoursesPrefs = getSharedPreferences("SavedCourses_" + username, MODE_PRIVATE);
        String savedCoursesString = savedCoursesPrefs.getString("courses", "");

        if (!savedCoursesString.isEmpty()) {
            // Check if this course is in the saved list
            String[] courseIds = savedCoursesString.split(",");
            for (String id : courseIds) {
                if (id.equals(String.valueOf(courseId))) {
                    isCourseSaved = true;
                    break;
                }
            }
        }

        // Update button text based on save status
        if (isCourseSaved) {
            btnSave.setText("Unsave");
        } else {
            btnSave.setText("Save");
        }
    }

    private void loadCourseData(final int courseId) {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                final Cursor cursor = dbManager.getCourseById(courseId);

                mainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (cursor != null && cursor.moveToFirst()) {
                            // Read data from cursor
                            int courseIndex = cursor.getColumnIndex("course");
                            int referenceIndex = cursor.getColumnIndex("reference");
                            int descriptionIndex = cursor.getColumnIndex("description");
                            int creditsIndex = cursor.getColumnIndex("credits");
                            int sessionIndex = cursor.getColumnIndex("session");
                            int startDateIndex = cursor.getColumnIndex("start_date");
                            int endDateIndex = cursor.getColumnIndex("end_date");
                            int instructorIndex = cursor.getColumnIndex("instructor");

                            // Store course name and reference for later use
                            courseName = cursor.getString(courseIndex);
                            courseReference = cursor.getString(referenceIndex);

                            // Populate UI with course data
                            tvCourseTitle.setText(courseName);
                            tvCourseReference.setText("Course Code: " + courseReference);
                            tvCourseDescription.setText(cursor.getString(descriptionIndex));
                            tvCredits.setText(cursor.getString(creditsIndex));

                            // Handle nullable fields
                            String session = sessionIndex >= 0 ? cursor.getString(sessionIndex) : "N/A";
                            String startDate = startDateIndex >= 0 ? cursor.getString(startDateIndex) : "N/A";
                            String endDate = endDateIndex >= 0 ? cursor.getString(endDateIndex) : "N/A";
                            String instructor = instructorIndex >= 0 ? cursor.getString(instructorIndex) : "TBA";

                            tvSession.setText(session);
                            tvStartDate.setText(startDate);
                            tvEndDate.setText(endDate);
                            tvInstructor.setText(instructor);

                            cursor.close();
                        } else {
                            Toast.makeText(CourseDetailsActivity.this,
                                    "Error loading course details",
                                    Toast.LENGTH_SHORT).show();
                            if (cursor != null) {
                                cursor.close();
                            }
                            finish();
                        }
                    }
                });
            }
        });
    }

    private void setupButtonListeners() {
        // Enroll button
        btnEnroll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enrollInCourse();
            }
        });

        // Save button
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isCourseSaved) {
                    unsaveCourse();
                } else {
                    saveCourse();
                }
            }
        });

        // Back button
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private void enrollInCourse() {
        // Get current username
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String username = prefs.getString("username", "");

        // For now, just show a success message
        // In a real app, you would add this to a student enrollments table
        Toast.makeText(this,
                "Successfully enrolled in " + courseName + " (" + courseReference + ")",
                Toast.LENGTH_LONG).show();

        // Add logging
        Log.d(TAG, "User " + username + " enrolled in course " + courseId +
                ": " + courseName + " (" + courseReference + ")");
    }

    private void saveCourse() {
        // Get current username
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String username = prefs.getString("username", "");

        // Get saved courses
        SharedPreferences savedCoursesPrefs = getSharedPreferences("SavedCourses_" + username, MODE_PRIVATE);
        String savedCoursesString = savedCoursesPrefs.getString("courses", "");

        // Add current course ID to saved courses
        if (savedCoursesString.isEmpty()) {
            savedCoursesString = String.valueOf(courseId);
        } else {
            // Check if course is already saved
            String[] savedCourseIds = savedCoursesString.split(",");
            Set<String> courseIdSet = new HashSet<>(Arrays.asList(savedCourseIds));

            if (!courseIdSet.contains(String.valueOf(courseId))) {
                savedCoursesString += "," + courseId;
            } else {
                Toast.makeText(this,
                        "Course already saved!",
                        Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Save updated list
        SharedPreferences.Editor editor = savedCoursesPrefs.edit();
        editor.putString("courses", savedCoursesString);
        editor.apply();

        // Update button and status
        isCourseSaved = true;
        btnSave.setText("Unsave");

        Toast.makeText(this,
                "Course " + courseName + " saved for later",
                Toast.LENGTH_LONG).show();

        // Add logging
        Log.d(TAG, "User " + username + " saved course " + courseId +
                ": " + courseName + " (" + courseReference + ")");
    }

    private void unsaveCourse() {
        // Get current username
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String username = prefs.getString("username", "");

        // Get saved courses
        SharedPreferences savedCoursesPrefs = getSharedPreferences("SavedCourses_" + username, MODE_PRIVATE);
        String savedCoursesString = savedCoursesPrefs.getString("courses", "");

        if (savedCoursesString.isEmpty()) {
            // Nothing to unsave
            return;
        }

        // Remove this course from the saved list
        String[] savedCourseIds = savedCoursesString.split(",");
        StringBuilder newSavedCourses = new StringBuilder();

        for (String id : savedCourseIds) {
            if (!id.equals(String.valueOf(courseId))) {
                if (newSavedCourses.length() > 0) {
                    newSavedCourses.append(",");
                }
                newSavedCourses.append(id);
            }
        }

        // Save updated list
        SharedPreferences.Editor editor = savedCoursesPrefs.edit();
        editor.putString("courses", newSavedCourses.toString());
        editor.apply();

        // Update button and status
        isCourseSaved = false;
        btnSave.setText("Save");

        Toast.makeText(this,
                "Course " + courseName + " removed from saved courses",
                Toast.LENGTH_LONG).show();

        // Add logging
        Log.d(TAG, "User " + username + " unsaved course " + courseId +
                ": " + courseName + " (" + courseReference + ")");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}