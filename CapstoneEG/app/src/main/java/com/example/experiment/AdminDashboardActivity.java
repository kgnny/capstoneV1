package com.example.experiment;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.content.SharedPreferences;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdminDashboardActivity extends Activity {
    private DatabaseManager dbManager;
    private ExecutorService executorService;
    private Handler mainThreadHandler;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        // Initialize thread management
        executorService = Executors.newFixedThreadPool(4);
        mainThreadHandler = new Handler(Looper.getMainLooper());

        // Initialize DatabaseManager
        dbManager = DatabaseManager.getInstance(this);

        // Find or create progress bar
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        if (progressBar == null) {
            progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleLarge);
            progressBar.setId(View.generateViewId());
            addContentView(progressBar, new android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
            progressBar.setX(getWindow().getDecorView().getWidth() / 2f - 50);
            progressBar.setY(getWindow().getDecorView().getHeight() / 2f - 50);
            progressBar.setVisibility(View.GONE);
        }

        // Find the import button
        Button btnImportCareers = (Button) findViewById(R.id.btnImportCareers);

        // Find the manage recommendations button
        Button btnManageRecommendations = (Button) findViewById(R.id.btnManageRecommendations);

        // Set click listener for import careers button
        btnImportCareers.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                importCareersFromAssets();
            }
        });

        // Set click listener for manage recommendations button
        btnManageRecommendations.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Show progress indicator if it exists
                if (progressBar != null) {
                    progressBar.setVisibility(View.VISIBLE);
                }

                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            // Check if courses exist, import if needed
                            final int courseCount = dbManager.getCourseCount();
                            Log.d("AdminDashboard", "Course count before launching ManageRecommendationsActivity: " + courseCount);

                            mainThreadHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (courseCount == 0) {
                                        // No courses found, import them first
                                        Toast.makeText(AdminDashboardActivity.this, "Importing courses before opening recommendations...", Toast.LENGTH_SHORT).show();
                                        importCoursesFromAssets();
                                        return;
                                    }

                                    // Hide progress indicator if it exists
                                    if (progressBar != null) {
                                        progressBar.setVisibility(View.GONE);
                                    }

                                    // Navigate to the ManageRecommendationsActivity
                                    Intent intent = new Intent(AdminDashboardActivity.this, ManageRecommendationsActivity.class);
                                    startActivity(intent);
                                }
                            });
                        } catch (final Exception e) {
                            mainThreadHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    // Hide progress indicator if it exists
                                    if (progressBar != null) {
                                        progressBar.setVisibility(View.GONE);
                                    }

                                    Log.e("AdminDashboard", "Error preparing to launch ManageRecommendationsActivity", e);
                                    Toast.makeText(AdminDashboardActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }
                });
            }
        });

        // Find the manage courses button
        Button btnManageCourses = (Button) findViewById(R.id.btnManageCourses);
        btnManageCourses.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("AdminDashboard", "Starting ManageCoursesActivity");
                try {
                    // Don't close the database before launching
                    Intent intent = new Intent(AdminDashboardActivity.this, ManageCoursesActivity.class);
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e("AdminDashboard", "Error starting ManageCoursesActivity", e);
                    Toast.makeText(AdminDashboardActivity.this, "Error opening Manage Courses: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });

        // Set click listener for Manage Appointment Availability button
        Button btnManageAvailability = (Button) findViewById(R.id.btnManageAvailability);
        // Set click listener for manage availability button
        btnManageAvailability.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Import administrators and launch the activity after successful import
                importAdministratorsFromAssets(true);
            }
        });
}
    public void logout(View view) {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear(); // Remove user data
        editor.apply();

        // Restart app at MainActivity (which will send user to LoginActivity)
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void importCareersFromAssets() {
        progressBar.setVisibility(View.VISIBLE);

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // Open CSV file
                    InputStream is = getAssets().open("occupation_15_filtered.csv");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));

                    // Read all lines
                    final List<String> lines = new ArrayList<>();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                    }
                    reader.close();

                    // Import data
                    final String[] linesArray = lines.toArray(new String[0]);
                    final boolean success = dbManager.importCareersFromCSV(linesArray);

                    // Check results
                    final int recordCount = dbManager.getCareerCount();

                    mainThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.GONE);

                            if (success) {
                                Toast.makeText(AdminDashboardActivity.this, "Successfully imported " + recordCount + " careers", Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(AdminDashboardActivity.this, "Error during import. Records found: " + recordCount, Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                } catch (final Exception e) {
                    mainThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.GONE);

                            Toast.makeText(AdminDashboardActivity.this, "Import error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            Log.e("AdminDashboardActivity", "Error importing careers", e);
                        }
                    });
                }
            }
        });
    }

    private void importAdministratorsFromAssets(final boolean launchActivityAfterImport) {
        progressBar.setVisibility(View.VISIBLE);

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // Open CSV file
                    InputStream is = getAssets().open("administration.csv");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));

                    // Read all lines
                    final List<String> lines = new ArrayList<>();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                    }
                    reader.close();

                    mainThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(AdminDashboardActivity.this, "Read " + lines.size() + " lines from CSV", Toast.LENGTH_SHORT).show();
                        }
                    });

                    // Import data
                    final String[] linesArray = lines.toArray(new String[0]);
                    final boolean success = dbManager.importAdministratorsFromCSV(linesArray);
                    final int recordCount = dbManager.getAdministratorCount();

                    mainThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.GONE);

                            if (success) {
                                Toast.makeText(AdminDashboardActivity.this, "Successfully imported " + recordCount + " administrators", Toast.LENGTH_LONG).show();

                                // Only launch if the flag is true
                                if (launchActivityAfterImport) {
                                    // Now we can start the ManageAvailabilityActivity
                                    Intent intent = new Intent(AdminDashboardActivity.this, ManageAvailabilityActivity.class);
                                    startActivity(intent);
                                }
                            } else {
                                Toast.makeText(AdminDashboardActivity.this, "Error during import", Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                } catch (final Exception e) {
                    mainThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.GONE);

                            Toast.makeText(AdminDashboardActivity.this, "Import error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            Log.e("AdminDashboardActivity", "Error importing administrators", e);
                        }
                    });
                }
            }
        });
    }

    private void importCoursesFromAssets() {
        progressBar.setVisibility(View.VISIBLE);

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
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

                    mainThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(AdminDashboardActivity.this, "Read " + lines.size() + " lines from courses CSV", Toast.LENGTH_SHORT).show();
                        }
                    });

                    // Import data
                    final String[] linesArray = lines.toArray(new String[0]);
                    final boolean success = dbManager.importCoursesFromCSV(linesArray);
                    final int recordCount = dbManager.getCourseCount();

                    mainThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.GONE);

                            if (success) {
                                Toast.makeText(AdminDashboardActivity.this, "Successfully imported " + recordCount + " courses", Toast.LENGTH_LONG).show();

                                // Check if we need to open the ManageRecommendationsActivity
                                if (recordCount > 0) {
                                    // Create the career_courses table if needed
                                    executorService.execute(new Runnable() {
                                        @Override
                                        public void run() {
                                            dbManager.createCareerCoursesTable();

                                            mainThreadHandler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    // Navigate to the ManageRecommendationsActivity
                                                    Intent intent = new Intent(AdminDashboardActivity.this, ManageRecommendationsActivity.class);
                                                    startActivity(intent);
                                                }
                                            });
                                        }
                                    });
                                }
                            } else {
                                Toast.makeText(AdminDashboardActivity.this, "Error during import", Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                } catch (final Exception e) {
                    mainThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.GONE);

                            Toast.makeText(AdminDashboardActivity.this, "Import error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            Log.e("AdminDashboardActivity", "Error importing courses", e);
                        }
                    });
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        // Shutdown executor service
        if (executorService != null) {
            executorService.shutdown();
        }

        // Don't close the database manager, just release the reference
        dbManager = null;

        super.onDestroy();
    }
}