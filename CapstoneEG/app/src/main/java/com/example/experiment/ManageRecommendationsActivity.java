package com.example.experiment;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ManageRecommendationsActivity extends Activity {
    private static final String TAG = "ManageRecommendations";

    private Spinner spinnerFields, spinnerCareers, spinnerCourses;
    private SeekBar seekBarRelevance;
    private TextView tvRelevanceValue;
    private Button btnAddRecommendation;
    private ListView listViewRecommendations;
    private ProgressBar progressBar;

    private DatabaseManager dbManager;
    private ExecutorService executorService;
    private Handler mainThreadHandler;

    private String selectedField = "All Fields";
    private int selectedCareerId = -1;
    private int selectedCourseId = -1;
    private int selectedRelevance = 5;

    private Map<String, List<DatabaseManager.CareerIdPair>> fieldOccupationMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_recommendations);

        executorService = Executors.newFixedThreadPool(2);
        mainThreadHandler = new Handler(Looper.getMainLooper());

        try {
            dbManager = DatabaseManager.getInstance(getApplicationContext());
        } catch (Exception e) {
            Log.e(TAG, "DatabaseManager initialization failed", e);
            Toast.makeText(this, "Failed to initialize database", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        spinnerFields = findViewById(R.id.spinnerFields);
        spinnerCareers = findViewById(R.id.spinnerCareers);
        spinnerCourses = findViewById(R.id.spinnerCourses);
        seekBarRelevance = findViewById(R.id.seekBarRelevance);
        tvRelevanceValue = findViewById(R.id.tvRelevanceValue);
        btnAddRecommendation = findViewById(R.id.btnAddRecommendation);
        listViewRecommendations = findViewById(R.id.listViewRecommendations);
        progressBar = findViewById(R.id.progressBar);

        initializeFieldOccupationMap();
        setupSpinners();
        setupSeekBar();
        setupAddButton();
        setupRecommendationList();

        verifyTablesAndLoadData();
    }

    private void verifyTablesAndLoadData() {
        progressBar.setVisibility(View.VISIBLE);
        executorService.execute(() -> {
            try {
                dbManager.createCareerCoursesTable();
                mainThreadHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    loadCareers();
                    loadCourses();
                    loadRecommendations();
                });
            } catch (Exception e) {
                Log.e(TAG, "Error verifying tables", e);
                mainThreadHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error initializing database", Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    private void loadCourses() {
        progressBar.setVisibility(View.VISIBLE);
        executorService.execute(() -> {
            List<String> courseNames = new ArrayList<>();
            Map<String, Integer> courseIdMap = new HashMap<>();
            try (Cursor cursor = dbManager.getCoursesByField(selectedField)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idIndex = cursor.getColumnIndex("_id");
                    int nameIndex = cursor.getColumnIndex("course");
                    do {
                        if (idIndex >= 0 && nameIndex >= 0) {
                            int id = cursor.getInt(idIndex);
                            String name = cursor.getString(nameIndex);
                            courseNames.add(name);
                            courseIdMap.put(name, id);
                        }
                    } while (cursor.moveToNext());
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to read courses from DB", e);
            }

            mainThreadHandler.post(() -> {
                progressBar.setVisibility(View.GONE);
                if (!courseNames.isEmpty()) {
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            this,
                            android.R.layout.simple_spinner_item,
                            courseNames);
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinnerCourses.setAdapter(adapter);

                    spinnerCourses.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            String selectedCourse = (String) parent.getItemAtPosition(position);
                            selectedCourseId = courseIdMap.getOrDefault(selectedCourse, -1);
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {
                            selectedCourseId = -1;
                        }
                    });
                } else {
                    spinnerCourses.setAdapter(new ArrayAdapter<>(
                            this,
                            android.R.layout.simple_spinner_item,
                            new String[]{"No courses available"}));
                }
            });
        });
    }

    private void loadRecommendations() {
        executorService.execute(() -> {
            try {
                List<DatabaseManager.Recommendation> recommendations = dbManager.getAllRecommendationsSafe();
                mainThreadHandler.post(() -> {
                    if (recommendations != null && !recommendations.isEmpty()) {
                        listViewRecommendations.setAdapter(new ArrayAdapter<>(
                                this,
                                android.R.layout.simple_list_item_1,
                                recommendations.stream().map(r -> r.recommendationInfo).toArray(String[]::new)));
                    } else {
                        listViewRecommendations.setAdapter(new ArrayAdapter<>(
                                this,
                                android.R.layout.simple_list_item_1,
                                new String[]{"No recommendations yet."}));
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to load recommendations", e);
                mainThreadHandler.post(() -> Toast.makeText(this, "Error loading recommendations", Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }

        // Don't close the database manager, just release the reference
        dbManager = null;

        super.onDestroy();
    }

    private void setupSpinners() {
        ArrayAdapter<String> fieldsAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, new ArrayList<>(fieldOccupationMap.keySet()));
        fieldsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFields.setAdapter(fieldsAdapter);

        spinnerFields.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedField = (String) parent.getItemAtPosition(position);
                loadCareers();
                loadCourses();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedField = "All Fields";
            }
        });

        spinnerCareers.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                List<DatabaseManager.CareerIdPair> careers = fieldOccupationMap.get(selectedField);
                if (careers != null && position >= 0 && position < careers.size()) {
                    selectedCareerId = careers.get(position).id;
                } else {
                    selectedCareerId = -1;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedCareerId = -1;
            }
        });

        spinnerCourses.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Object item = parent.getItemAtPosition(position);
                if (item instanceof Cursor) {
                    Cursor cursor = (Cursor) item;
                    int idColumnIndex = cursor.getColumnIndex("_id");
                    if (idColumnIndex != -1) {
                        selectedCourseId = cursor.getInt(idColumnIndex);
                    }
                } else {
                    selectedCourseId = -1;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedCourseId = -1;
            }
        });
    }

    private void setupSeekBar() {
        seekBarRelevance.setMax(10);
        seekBarRelevance.setProgress(selectedRelevance);
        tvRelevanceValue.setText(selectedRelevance + "/10");

        seekBarRelevance.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                selectedRelevance = progress;
                tvRelevanceValue.setText(progress + "/10");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void setupAddButton() {
        btnAddRecommendation.setOnClickListener(v -> {
            if (selectedCareerId != -1 && selectedCourseId != -1) {
                addOrUpdateRecommendation();
            } else {
                Toast.makeText(this, "Please select both a career and a course", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupRecommendationList() {
        listViewRecommendations.setOnItemClickListener((parent, view, position, id) ->
                showDeleteConfirmationDialog((int) id));
    }

    private void initializeFieldOccupationMap() {
        fieldOccupationMap = new HashMap<>();
        String[] fields = {"All Fields", "STEM", "Health Science", "Business", "Education", "Arts"};
        for (String field : fields) fieldOccupationMap.put(field, new ArrayList<>());

        List<DatabaseManager.CareerIdPair> allCareers = dbManager.getCareerIdTitlePairs();
        fieldOccupationMap.get("All Fields").addAll(allCareers);

        for (DatabaseManager.CareerIdPair career : allCareers) {
            String title = career.title.toLowerCase();
            if (title.matches(".*(engineer|developer|programmer|scientist|analyst|technician|math|computer|science|research|technology|data|it|system).*"))
                fieldOccupationMap.get("STEM").add(career);
            if (title.matches(".*(health|medical|nurse|doctor|therapist|pharmacist|clinical|hospital|healthcare).*"))
                fieldOccupationMap.get("Health Science").add(career);
            if (title.matches(".*(manager|executive|director|finance|business|consult|sales|account).*"))
                fieldOccupationMap.get("Business").add(career);
            if (title.matches(".*(teacher|professor|educator|trainer|tutor|education|school).*"))
                fieldOccupationMap.get("Education").add(career);
            if (title.matches(".*(artist|designer|musician|actor|director|perform|media|illustrator).*"))
                fieldOccupationMap.get("Arts").add(career);
        }
    }

    private void loadCareers() {
        List<DatabaseManager.CareerIdPair> careers = fieldOccupationMap.get(selectedField);
        if (careers == null) careers = new ArrayList<>();
        List<String> careerTitles = new ArrayList<>();
        for (DatabaseManager.CareerIdPair c : careers) careerTitles.add(c.title);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, careerTitles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCareers.setAdapter(adapter);
    }

    private void addOrUpdateRecommendation() {
        progressBar.setVisibility(View.VISIBLE);
        executorService.execute(() -> {
            boolean success = dbManager.updateOrAddRecommendedCourse(selectedCareerId, selectedCourseId, selectedRelevance);
            mainThreadHandler.post(() -> {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(this, success ? "Recommendation saved" : "Failed to save", Toast.LENGTH_SHORT).show();
                loadRecommendations();
            });
        });
    }

    private void showDeleteConfirmationDialog(final int recommendationId) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Recommendation")
                .setMessage("Are you sure you want to delete this recommendation?")
                .setPositiveButton("Yes", (dialog, which) -> deleteRecommendation(recommendationId))
                .setNegativeButton("No", null)
                .show();
    }

    private void deleteRecommendation(final int recommendationId) {
        executorService.execute(() -> {
            boolean success = dbManager.deleteRecommendation(recommendationId);
            mainThreadHandler.post(() -> {
                Toast.makeText(this, success ? "Recommendation deleted" : "Failed to delete", Toast.LENGTH_SHORT).show();
                loadRecommendations();
            });
        });
    }


}
