package com.example.experiment;

import static android.content.ContentValues.TAG;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.database.MatrixCursor;
import android.database.MergeCursor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ManageCoursesActivity extends Activity {
    private static final String TAG = "ManageCoursesActivity";
    private boolean importInProgress = false;
    private DatabaseManager dbManager;
    private EditText editTextSearch;
    private Button btnSearch;
    private Spinner spinnerPrograms;
    private Button btnAddNewCourse;
    private ListView listViewCourses;
    private Button btnImportCourses;

    // Add ExecutorService and Handler for background operations
    private ExecutorService executorService;
    private Handler mainThreadHandler;

    private SimpleCursorAdapter adapter;
    private int selectedProgramId = -1;

    // Flag to track activity state
    private volatile boolean isActivityActive = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_courses);
        Log.d(TAG, "onCreate started");

        try {
            // Reset state
            isActivityActive = true;
            importInProgress = false;

            // Initialize ExecutorService and Handler
            if (executorService == null || executorService.isShutdown()) {
                executorService = Executors.newFixedThreadPool(2);
            }

            mainThreadHandler = new Handler(Looper.getMainLooper());

            // Get the database manager instance consistently
            try {
                // Always use getInstance for consistency
                dbManager = DatabaseManager.getInstance(this);
            } catch (Exception e) {
                Log.e(TAG, "Error getting DatabaseManager", e);
                Toast.makeText(this, "Error initializing database: " + e.getMessage(), Toast.LENGTH_LONG).show();
                finish(); // Close the activity on fatal database error
                return;
            }

            // Initialize UI components
            editTextSearch = findViewById(R.id.editTextSearchCourse);
            btnSearch = findViewById(R.id.btnSearchCourse);
            spinnerPrograms = findViewById(R.id.spinnerPrograms);
            btnAddNewCourse = findViewById(R.id.btnAddNewCourse);
            listViewCourses = findViewById(R.id.listViewCourses);
            btnImportCourses = findViewById(R.id.btnImportCourses);

            // Set up event handlers
            setupEventHandlers();

            // Add a placeholder adapter for the spinner until academic programs load
            ArrayAdapter<String> placeholderAdapter = new ArrayAdapter<>(
                    this, android.R.layout.simple_spinner_item,
                    new String[]{"Loading programs..."});
            placeholderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerPrograms.setAdapter(placeholderAdapter);

            // Show a placeholder for courses list - do this asynchronously
            loadCourses("");

            // Start background operations
            if (isActivityActive) {
                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (!isActivityActive) return;

                        try {
                            // Ensure tables exist in background
                            dbManager.ensureAcademicProgramsTableExists();
                            dbManager.ensureCoursesTableColumns();

                            if (!isActivityActive) return;

                            // Check if we need to import courses
                            final int courseCount = dbManager.getCourseCount();

                            if (!isActivityActive) return;

                            mainThreadHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (!isActivityActive || isFinishing()) return;

                                    if (courseCount == 0) {
                                        importCoursesFromAssets();
                                    }
                                }
                            });

                            if (!isActivityActive) return;

                            // Load academic programs
                            final int programCount = dbManager.getAcademicProgramCount();

                            if (!isActivityActive) return;

                            mainThreadHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (!isActivityActive || isFinishing()) return;

                                    if (programCount == 0) {
                                        importAcademicProgramsFromAssets();
                                    } else {
                                        loadAcademicPrograms();
                                    }
                                }
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "Error in background task", e);
                        }
                    }
                });
            }

        } catch (Exception e) {
            Log.e(TAG, "Error initializing activity", e);
            Toast.makeText(this, "Error initializing: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish(); // Close the activity on fatal error
        }

        Log.d(TAG, "onCreate completed");
    }

    // Helper method to set up event handlers
    private void setupEventHandlers() {
        try {
            // Handle program selection for filtering
            spinnerPrograms.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @SuppressLint("Range")
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    try {
                        if (!isActivityActive) return;

                        // Safe type checking
                        Object item = parent.getItemAtPosition(position);
                        if (item instanceof Cursor) {
                            Cursor cursor = (Cursor) item;
                            selectedProgramId = cursor.getInt(cursor.getColumnIndex("_id"));
                        } else if (item instanceof String) {
                            // Handle string item (from fallback adapter)
                            Log.d(TAG, "String item selected: " + item);
                            selectedProgramId = -1;
                        } else {
                            // Handle other types
                            Log.d(TAG, "Unknown item type selected: " +
                                    (item != null ? item.getClass().getName() : "null"));
                            selectedProgramId = -1;
                        }

                        // Refresh the course list with the filter
                        loadCourses(editTextSearch.getText().toString());
                    } catch (Exception e) {
                        Log.e(TAG, "Error in onItemSelected", e);
                        // If there's an error, just reset to no filter
                        selectedProgramId = -1;
                        if (isActivityActive) {
                            loadCourses(editTextSearch.getText().toString());
                        }
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    selectedProgramId = -1;
                    if (isActivityActive) {
                        loadCourses(editTextSearch.getText().toString());
                    }
                }
            });

            // Search button click event
            btnSearch.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!isActivityActive) return;
                    String query = editTextSearch.getText().toString().trim();
                    loadCourses(query);
                }
            });

            // Course list item click event
            listViewCourses.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    if (!isActivityActive) return;
                    showCourseOptionsDialog((int) id);
                }
            });

            // Add new course button click event
            btnAddNewCourse.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!isActivityActive) return;
                    showAddCourseDialog();
                }
            });

            // Import courses button click event
            btnImportCourses.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!isActivityActive) return;

                    if (!importInProgress) {
                        importCoursesFromAssets();
                    } else {
                        Toast.makeText(ManageCoursesActivity.this,
                                "Import already in progress, please wait...",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up event handlers", e);
        }
    }

    private void loadAcademicPrograms() {
        if (!isActivityActive) return;

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!isActivityActive) return;

                    // Ensure academic programs table exists
                    dbManager.ensureAcademicProgramsTableExists();

                    // Ensure academic programs are imported
                    if (dbManager.getAcademicProgramCount() == 0) {
                        importAcademicProgramsFromAssets();
                        return;
                    }

                    if (!isActivityActive) return;

                    final SimpleCursorAdapter programsAdapter;
                    try {
                        // Get cursor with academic programs
                        programsAdapter = getSimpleCursorAdapter();

                        if (!isActivityActive) return;

                        mainThreadHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (!isActivityActive) return;

                                try {
                                    // Check if spinnerPrograms is still valid before setting adapter
                                    if (spinnerPrograms != null && !isFinishing()) {
                                        spinnerPrograms.setAdapter(programsAdapter);
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error setting spinner adapter", e);
                                }
                            }
                        });
                    } catch (final Exception e) {
                        Log.e(TAG, "Error creating program adapter", e);

                        if (!isActivityActive) return;

                        mainThreadHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (!isActivityActive || isFinishing()) return;

                                // Add a string-based adapter as fallback
                                try {
                                    if (spinnerPrograms != null) {
                                        ArrayAdapter<String> fallbackAdapter = new ArrayAdapter<>(
                                                ManageCoursesActivity.this,
                                                android.R.layout.simple_spinner_item,
                                                new String[]{"All Programs"});
                                        fallbackAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                                        spinnerPrograms.setAdapter(fallbackAdapter);
                                    }
                                } catch (Exception ex) {
                                    Log.e(TAG, "Error setting fallback adapter", ex);
                                }
                            }
                        });
                    }
                } catch (final Exception e) {
                    Log.e(TAG, "Error loading academic programs", e);

                    if (!isActivityActive) return;

                    mainThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (!isActivityActive || isFinishing()) return;

                            // Add a default item to the spinner if programs can't be loaded
                            try {
                                if (spinnerPrograms != null) {
                                    ArrayAdapter<String> defaultAdapter = new ArrayAdapter<>(
                                            ManageCoursesActivity.this,
                                            android.R.layout.simple_spinner_item,
                                            new String[]{"All Programs"});
                                    defaultAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                                    spinnerPrograms.setAdapter(defaultAdapter);
                                }
                            } catch (Exception ex) {
                                Log.e(TAG, "Error setting default adapter", ex);
                            }
                        }
                    });
                }
            }
        });
    }

    private SimpleCursorAdapter getSimpleCursorAdapter() {
        try {
            // First, get all academic programs
            Cursor cursor = dbManager.getAllAcademicPrograms();
            if (cursor == null || cursor.getCount() == 0) {
                // Return a placeholder adapter
                MatrixCursor matrixCursor = new MatrixCursor(new String[]{"_id", "program_name"});
                matrixCursor.addRow(new Object[]{-1, "All Programs"});

                return new SimpleCursorAdapter(
                        this,
                        android.R.layout.simple_spinner_item,
                        matrixCursor,
                        new String[]{"program_name"},
                        new int[]{android.R.id.text1},
                        0
                );
            }

            // Create a MatrixCursor to add our "Select a program" option
            String[] columnNames = {"_id", "program_name"};
            MatrixCursor matrixCursor = new MatrixCursor(columnNames);

            // Add the "Select a program" row with ID -1 (this will be our indicator for "no filter")
            matrixCursor.addRow(new Object[]{-1, "Select a program"});

            // Combine the cursors
            MergeCursor mergeCursor = new MergeCursor(new Cursor[]{matrixCursor, cursor});

            // Create adapter for spinner
            String[] fromColumns = {"program_name"};
            int[] toViews = {android.R.id.text1};

            SimpleCursorAdapter programsAdapter = new SimpleCursorAdapter(
                    this,
                    android.R.layout.simple_spinner_item,
                    mergeCursor,
                    fromColumns,
                    toViews,
                    0
            );

            programsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            return programsAdapter;
        } catch (Exception e) {
            Log.e(TAG, "Error in getSimpleCursorAdapter", e);

            // Create a fallback cursor with just the "All Programs" option
            MatrixCursor matrixCursor = new MatrixCursor(new String[]{"_id", "program_name"});
            matrixCursor.addRow(new Object[]{-1, "All Programs"});

            SimpleCursorAdapter fallbackAdapter = new SimpleCursorAdapter(
                    this,
                    android.R.layout.simple_spinner_item,
                    matrixCursor,
                    new String[]{"program_name"},
                    new int[]{android.R.id.text1},
                    0
            );

            fallbackAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            return fallbackAdapter;
        }
    }

    private void loadCourses(final String searchQuery) {
        if (!isActivityActive) return;

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!isActivityActive) return;

                    final Cursor cursor;

                    // First try to get the courses with proper error handling
                    try {
                        if (selectedProgramId == -1) {
                            // No program filter, just search query if any
                            if (TextUtils.isEmpty(searchQuery)) {
                                cursor = dbManager.getAllCourses();
                            } else {
                                cursor = dbManager.searchCourses(searchQuery);
                            }
                        } else {
                            // Filter by program and search query
                            if (TextUtils.isEmpty(searchQuery)) {
                                cursor = dbManager.getCoursesForProgram(selectedProgramId);
                            } else {
                                cursor = dbManager.searchCoursesInProgram(selectedProgramId, searchQuery);
                            }
                        }

                        // Check for null cursor
                        if (cursor == null) {
                            Log.e(TAG, "Received null cursor when loading courses");
                            mainThreadHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (!isActivityActive) return;
                                    Toast.makeText(ManageCoursesActivity.this,
                                            "Error loading courses: Database connection issue",
                                            Toast.LENGTH_SHORT).show();
                                }
                            });
                            return;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Exception getting cursor: " + e.getMessage());
                        mainThreadHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (!isActivityActive) return;
                                Toast.makeText(ManageCoursesActivity.this,
                                        "Error loading courses: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                        return;
                    }

                    if (!isActivityActive) {
                        if (cursor != null && !cursor.isClosed()) {
                            cursor.close();
                        }
                        return;
                    }

                    mainThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (!isActivityActive) {
                                if (cursor != null && !cursor.isClosed()) {
                                    cursor.close();
                                }
                                return;
                            }

                            try {
                                // Columns from the database to use
                                String[] fromColumns = {"course", "reference", "description"};

                                // IDs of views to map the columns to
                                int[] toViews = {R.id.tvCourseTitle, R.id.tvCourseReference, R.id.tvCourseDetails};

                                // Close the old adapter's cursor if it exists
                                if (adapter != null) {
                                    Cursor oldCursor = adapter.getCursor();
                                    if (oldCursor != null && !oldCursor.isClosed()) {
                                        oldCursor.close();
                                    }
                                }

                                // Create adapter with custom view binder to show program information
                                adapter = new SimpleCursorAdapter(
                                        ManageCoursesActivity.this,
                                        R.layout.item_course,
                                        cursor,
                                        fromColumns,
                                        toViews,
                                        0
                                );

                                // Add a ViewBinder to display program assignments
                                adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
                                    @Override
                                    public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                                        if (view.getId() == R.id.tvCourseTitle) {
                                            // Get course title and ID
                                            String courseTitle = cursor.getString(columnIndex);
                                            TextView titleView = (TextView) view;
                                            titleView.setText(courseTitle);

                                            // Find course ID
                                            @SuppressLint("Range") int courseId = cursor.getInt(cursor.getColumnIndex("_id"));

                                            // Find program info text view (must be added to your item_course.xml layout)
                                            View parent = (View) view.getParent();
                                            TextView programsView = (TextView) parent.findViewById(R.id.tvCoursePrograms);

                                            if (programsView != null && isActivityActive) {
                                                // Load program information asynchronously
                                                updateProgramsForCourse(courseId, programsView);
                                            }

                                            return true;
                                        }
                                        return false;
                                    }
                                });

                                // Set adapter to ListView
                                listViewCourses.setAdapter(adapter);
                            } catch (Exception e) {
                                Log.e(TAG, "Error creating course adapter", e);

                                // Close cursor if adapter creation failed
                                if (cursor != null && !cursor.isClosed()) {
                                    cursor.close();
                                }
                            }
                        }
                    });
                } catch (final Exception e) {
                    Log.e(TAG, "Error loading courses", e);
                    if (!isActivityActive) return;

                    mainThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (!isActivityActive) return;

                            Toast.makeText(ManageCoursesActivity.this,
                                    "Error loading courses: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }

    private void updateProgramsForCourse(final int courseId, final TextView programsView) {
        if (!isActivityActive) return;

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                if (!isActivityActive) return;

                // Get programs for this course
                final StringBuilder programsText = new StringBuilder("Programs: ");
                boolean hasPrograms = false;
                Cursor programsCursor = null;

                try {
                    programsCursor = dbManager.getProgramsForCourse(courseId);
                    if (programsCursor != null && programsCursor.getCount() > 0) {
                        int programNameIndex = programsCursor.getColumnIndex("program_name");
                        if (programNameIndex >= 0) {
                            while (programsCursor.moveToNext()) {
                                if (hasPrograms) {
                                    programsText.append(", ");
                                }
                                programsText.append(programsCursor.getString(programNameIndex));
                                hasPrograms = true;
                            }
                        } else {
                            Log.e(TAG, "program_name column not found in cursor");
                            programsText.append("Unknown programs");
                            hasPrograms = true;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error getting programs for course", e);
                } finally {
                    if (programsCursor != null) {
                        programsCursor.close();
                    }
                }

                if (!hasPrograms) {
                    programsText.append("None");
                }

                if (!isActivityActive) return;

                mainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!isActivityActive) return;

                        try {
                            programsView.setText(programsText.toString());
                        } catch (Exception e) {
                            Log.e(TAG, "Error updating programs view", e);
                        }
                    }
                });
            }
        });
    }

    private void importCoursesFromAssets() {
        if (!isActivityActive) return;

        // Prevent multiple simultaneous imports
        if (importInProgress) {
            Toast.makeText(this, "Import already in progress, please wait...",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        importInProgress = true;

        // Show a progress dialog
        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Importing courses...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!isActivityActive) {
                        return;
                    }

                    // Open CSV file
                    InputStream is = getAssets().open("mdc_courses.csv");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));

                    // Read all lines
                    final List<String> lines = new ArrayList<>();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                    }
                    reader.close();

                    // Convert to array outside UI thread
                    final String[] linesArray = lines.toArray(new String[0]);

                    if (!isActivityActive) {
                        return;
                    }

                    // Update UI before importing
                    mainThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (!isActivityActive) {
                                progressDialog.dismiss();
                                return;
                            }

                            Toast.makeText(ManageCoursesActivity.this,
                                    "Read " + lines.size() + " lines from courses CSV",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });

                    if (!isActivityActive) {
                        progressDialog.dismiss();
                        return;
                    }

                    // Import data
                    final boolean success = dbManager.importCoursesFromCSV(linesArray);
                    final int recordCount = dbManager.getCourseCount();

                    if (!isActivityActive) {
                        progressDialog.dismiss();
                        return;
                    }

                    // Update UI after importing
                    mainThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            importInProgress = false;  // Reset flag when done

                            if (!isActivityActive) {
                                progressDialog.dismiss();
                                return;
                            }

                            try {
                                progressDialog.dismiss();

                                if (success) {
                                    Toast.makeText(ManageCoursesActivity.this,
                                            "Successfully imported " + recordCount + " courses",
                                            Toast.LENGTH_LONG).show();
                                    // Refresh the course list
                                    loadCourses("");
                                } else {
                                    Toast.makeText(ManageCoursesActivity.this,
                                            "Error during import",
                                            Toast.LENGTH_LONG).show();
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error updating UI after import", e);
                            }
                        }
                    });
                } catch (final Exception e) {
                    // Log the exception
                    Log.e(TAG, "Error importing courses", e);

                    if (!isActivityActive) {
                        return;
                    }

                    // Update UI in case of error
                    mainThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            importInProgress = false;  // Reset flag on error too

                            if (!isActivityActive) {
                                progressDialog.dismiss();
                                return;
                            }

                            try {
                                progressDialog.dismiss();
                                Toast.makeText(ManageCoursesActivity.this,
                                        "Import error: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            } catch (Exception ex) {
                                Log.e(TAG, "Error dismissing dialog", ex);
                            }
                        }
                    });
                }
            }
        });
    }

    private void importAcademicProgramsFromAssets() {
        if (!isActivityActive) return;

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!isActivityActive) return;

                    // Open CSV file
                    InputStream is = getAssets().open("academic_programs.csv");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));

                    // Read all lines
                    List<String> lines = new ArrayList<>();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                    }
                    reader.close();

                    if (!isActivityActive) return;

                    // Import data
                    final String[] linesArray = lines.toArray(new String[0]);
                    dbManager.importAcademicProgramsFromCSV(linesArray);

                    if (!isActivityActive) return;

                    // Update UI on success
                    mainThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (!isActivityActive) return;
                            loadAcademicPrograms();
                        }
                    });

                } catch (final Exception e) {
                    Log.e(TAG, "Error importing academic programs", e);

                    if (!isActivityActive) return;

                    mainThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (!isActivityActive) return;

                            Toast.makeText(ManageCoursesActivity.this,
                                    "Error importing programs: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }

    private void showCourseOptionsDialog(final int courseId) {
        if (!isActivityActive) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Course Options");

        String[] options = {"Edit Course", "Assign to Programs", "Delete Course", "Cancel"};

        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (!isActivityActive) {
                    dialog.dismiss();
                    return;
                }

                switch (which) {
                    case 0: // Edit Course
                        showEditCourseDialog(courseId);
                        break;
                    case 1: // Assign to Programs
                        showAssignProgramsDialog(courseId);
                        break;
                    case 2: // Delete Course
                        showDeleteCourseConfirmation(courseId);
                        break;
                    case 3: // Cancel
                        dialog.dismiss();
                        break;
                    default:
                        dialog.dismiss();
                        break;
                }
            }
        });

        builder.show();
    }

    private void showDeleteCourseConfirmation(final int courseId) {
        if (!isActivityActive) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Delete Course");
        builder.setMessage("Are you sure you want to delete this course?");

        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (!isActivityActive) return;

                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (!isActivityActive) return;

                        final boolean success = dbManager.deleteCourse(courseId);

                        if (!isActivityActive) return;

                        mainThreadHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (!isActivityActive) return;

                                if (success) {
                                    Toast.makeText(ManageCoursesActivity.this, "Course deleted successfully", Toast.LENGTH_SHORT).show();
                                    loadCourses("");
                                } else {
                                    Toast.makeText(ManageCoursesActivity.this, "Failed to delete course", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    }
                });
            }
        });

        builder.setNegativeButton("No", null);
        builder.show();
    }

    private void showAssignProgramsDialog(final int courseId) {
        if (!isActivityActive) return;

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                if (!isActivityActive) return;

                // Get all available programs
                final Cursor allProgramsCursor = dbManager.getAllAcademicPrograms();

                if (!isActivityActive) {
                    if (allProgramsCursor != null) {
                        allProgramsCursor.close();
                    }
                    return;
                }

                mainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!isActivityActive) {
                            if (allProgramsCursor != null) {
                                allProgramsCursor.close();
                            }
                            return;
                        }

                        if (allProgramsCursor == null || allProgramsCursor.getCount() == 0) {
                            Toast.makeText(ManageCoursesActivity.this, "No programs available", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // Create list of program names and IDs
                        final List<String> programNames = new ArrayList<>();
                        final List<Integer> programIds = new ArrayList<>();
                        final boolean[] checkedItems = new boolean[allProgramsCursor.getCount()];

                        int index = 0;
                        while (allProgramsCursor.moveToNext()) {
                            try {
                                int idColumnIndex = allProgramsCursor.getColumnIndex("_id");
                                int nameColumnIndex = allProgramsCursor.getColumnIndex("program_name");

                                if (idColumnIndex >= 0 && nameColumnIndex >= 0) {
                                    int programId = allProgramsCursor.getInt(idColumnIndex);
                                    String programName = allProgramsCursor.getString(nameColumnIndex);

                                    programNames.add(programName);
                                    programIds.add(programId);

                                    // Check if this course is already in this program
                                    checkedItems[index] = dbManager.isCourseInProgram(courseId, programId);
                                    index++;
                                } else {
                                    Log.e(TAG, "Column not found in cursor: " +
                                            (idColumnIndex < 0 ? "_id " : "") +
                                            (nameColumnIndex < 0 ? "program_name" : ""));
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error processing program cursor row", e);
                            }
                        }

                        // Create dialog with multi-choice items
                        AlertDialog.Builder builder = new AlertDialog.Builder(ManageCoursesActivity.this);
                        builder.setTitle("Assign Course to Programs");

                        builder.setMultiChoiceItems(
                                programNames.toArray(new String[0]),
                                checkedItems,
                                new DialogInterface.OnMultiChoiceClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                                        // Update checked status
                                        checkedItems[which] = isChecked;
                                    }
                                }
                        );

                        builder.setPositiveButton("Save", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (!isActivityActive) return;

                                // Update program assignments based on checked items
                                executorService.execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (!isActivityActive) return;

                                        for (int i = 0; i < programIds.size(); i++) {
                                            int programId = programIds.get(i);
                                            boolean isChecked = checkedItems[i];
                                            boolean wasChecked = dbManager.isCourseInProgram(courseId, programId);

                                            if (isChecked && !wasChecked) {
                                                // Add course to program
                                                dbManager.addCourseToProgram(courseId, programId);
                                            } else if (!isChecked && wasChecked) {
                                                // Remove course from program
                                                dbManager.removeCourseFromProgram(courseId, programId);
                                            }
                                        }

                                        if (!isActivityActive) return;

                                        mainThreadHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (!isActivityActive) return;

                                                // Refresh the course list to reflect any changes to filters
                                                loadCourses(editTextSearch.getText().toString());
                                                Toast.makeText(ManageCoursesActivity.this, "Program assignments updated", Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                    }
                                });
                            }
                        });

                        builder.setNegativeButton("Cancel", null);
                        builder.show();

                        // Close cursor when dialog is dismissed
                        allProgramsCursor.close();
                    }
                });
            }
        });
    }

    private void showAddCourseDialog() {
        if (!isActivityActive) return;

        // Implementation for adding a new course
        // This would show a dialog with fields for all course attributes
        // For brevity, this is a simplified version

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add New Course");

        // Inflate a custom layout for the dialog
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_edit_course, null);
        builder.setView(dialogView);

        // Find dialog views
        final EditText editTextCourse = (EditText) dialogView.findViewById(R.id.editTextCourse);
        final EditText editTextReference = (EditText) dialogView.findViewById(R.id.editTextReference);
        final EditText editTextCredits = (EditText) dialogView.findViewById(R.id.editTextCredits);
        final EditText editTextDescription = (EditText) dialogView.findViewById(R.id.editTextDescription);
        // Add other fields as needed

        builder.setPositiveButton("Add", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (!isActivityActive) return;

                final String course = editTextCourse.getText().toString().trim();
                final String reference = editTextReference.getText().toString().trim();
                final String credits = editTextCredits.getText().toString().trim();
                final String description = editTextDescription.getText().toString().trim();

                if (!TextUtils.isEmpty(course) && !TextUtils.isEmpty(reference)) {
                    executorService.execute(new Runnable() {
                        @Override
                        public void run() {
                            if (!isActivityActive) return;

                            final boolean success = dbManager.addCourse(course, reference, credits, description);

                            if (!isActivityActive) return;

                            mainThreadHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (!isActivityActive) return;

                                    if (success) {
                                        Toast.makeText(ManageCoursesActivity.this, "Course added successfully", Toast.LENGTH_SHORT).show();
                                        loadCourses("");
                                    } else {
                                        Toast.makeText(ManageCoursesActivity.this, "Failed to add course", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                        }
                    });
                } else {
                    Toast.makeText(ManageCoursesActivity.this, "Course and reference are required", Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.setNegativeButton("Cancel", null);

        builder.show();
    }

    private void showEditCourseDialog(final int courseId) {
        if (!isActivityActive) return;

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                if (!isActivityActive) return;

                // Implementation for editing an existing course
                // Similar to add, but pre-populates the dialog with existing data
                final Cursor cursor = dbManager.getCourseById(courseId);

                if (!isActivityActive) {
                    if (cursor != null) {
                        cursor.close();
                    }
                    return;
                }

                mainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!isActivityActive) {
                            if (cursor != null) {
                                cursor.close();
                            }
                            return;
                        }

                        if (cursor != null && cursor.moveToFirst()) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(ManageCoursesActivity.this);
                            builder.setTitle("Edit Course");

                            // Inflate custom layout
                            View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_edit_course, null);
                            builder.setView(dialogView);

                            // Find dialog views
                            final EditText editTextCourse = (EditText) dialogView.findViewById(R.id.editTextCourse);
                            final EditText editTextReference = (EditText) dialogView.findViewById(R.id.editTextReference);
                            final EditText editTextCredits = (EditText) dialogView.findViewById(R.id.editTextCredits);
                            final EditText editTextDescription = (EditText) dialogView.findViewById(R.id.editTextDescription);
                            // Add other fields as needed

                            // Pre-populate fields
                            // Pre-populate fields
                            try {
                                int courseIndex = cursor.getColumnIndex("course");
                                int referenceIndex = cursor.getColumnIndex("reference");
                                int creditsIndex = cursor.getColumnIndex("credits");
                                int descriptionIndex = cursor.getColumnIndex("description");

                                if (courseIndex >= 0) {
                                    editTextCourse.setText(cursor.getString(courseIndex));
                                }

                                if (referenceIndex >= 0) {
                                    editTextReference.setText(cursor.getString(referenceIndex));
                                }

                                if (creditsIndex >= 0) {
                                    editTextCredits.setText(cursor.getString(creditsIndex));
                                }

                                if (descriptionIndex >= 0) {
                                    editTextDescription.setText(cursor.getString(descriptionIndex));
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error pre-populating course data", e);
                            }
                            cursor.close();

                            builder.setPositiveButton("Update", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (!isActivityActive) return;

                                    final String course = editTextCourse.getText().toString().trim();
                                    final String reference = editTextReference.getText().toString().trim();
                                    final String credits = editTextCredits.getText().toString().trim();
                                    final String description = editTextDescription.getText().toString().trim();

                                    if (!TextUtils.isEmpty(course) && !TextUtils.isEmpty(reference)) {
                                        executorService.execute(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (!isActivityActive) return;

                                                final boolean success = dbManager.updateCourse(courseId, course, reference, credits, description);

                                                if (!isActivityActive) return;

                                                mainThreadHandler.post(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        if (!isActivityActive) return;

                                                        if (success) {
                                                            Toast.makeText(ManageCoursesActivity.this, "Course updated successfully", Toast.LENGTH_SHORT).show();
                                                            loadCourses("");
                                                        } else {
                                                            Toast.makeText(ManageCoursesActivity.this, "Failed to update course", Toast.LENGTH_SHORT).show();
                                                        }
                                                    }
                                                });
                                            }
                                        });
                                    } else {
                                        Toast.makeText(ManageCoursesActivity.this, "Course and reference are required", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });

                            builder.setNegativeButton("Cancel", null);
                            builder.show();
                        }
                    }
                });
            }
        });
    }

    // Lifecycle methods

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause called");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop called");
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy started");

        // Mark activity as inactive to prevent callbacks
        isActivityActive = false;

        try {
            // Remove callbacks
            if (mainThreadHandler != null) {
                mainThreadHandler.removeCallbacksAndMessages(null);
            }

            // Close any cursor in the adapter
            if (adapter != null) {
                try {
                    Cursor cursor = adapter.getCursor();
                    if (cursor != null && !cursor.isClosed()) {
                        cursor.close();
                    }
                    adapter = null;
                } catch (Exception e) {
                    Log.e(TAG, "Error closing adapter cursor", e);
                }
            }

            // Shutdown executor service immediately
            if (executorService != null && !executorService.isShutdown()) {
                try {
                    List<Runnable> pendingTasks = executorService.shutdownNow();
                    Log.d(TAG, "Shutdown executor service with " + pendingTasks.size() + " pending tasks");
                    executorService = null;
                } catch (Exception e) {
                    Log.e(TAG, "Error shutting down executor service", e);
                }
            }

            // Don't close the database manager, just release the reference
            // This allows other activities to continue using the singleton instance
            dbManager = null;

        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy", e);
        } finally {
            Log.d(TAG, "onDestroy completed, calling super.onDestroy()");
            super.onDestroy();
        }
    }
}