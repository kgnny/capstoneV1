package com.example.experiment;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ManageAvailabilityActivity extends Activity {
    private static final String TAG = "ManageAvailability";
    private DatabaseManager dbManager;
    private Spinner spinnerAdmin;
    private Button btnSelectDate;
    private TextView tvSelectedDate;
    private Spinner spinnerTimeSlot;
    private Button btnAddTimeSlot;
    private ListView listViewTimeSlots;

    private Calendar calendar;
    private SimpleDateFormat dateFormat;
    private int selectedAdminId = -1;
    private String selectedDate = null;
    private String[] timeSlots = new String[]{
            "10:00 AM", "10:30 AM", "11:00 AM", "11:30 AM",
            "12:00 PM", "12:30 PM", "1:00 PM", "1:30 PM",
            "2:00 PM", "2:30 PM", "3:00 PM", "3:30 PM", "4:00 PM"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_availability);

        Log.d(TAG, "onCreate: Starting ManageAvailabilityActivity");

        // Initialize database manager
        dbManager = new DatabaseManager(this);

        // Set up calendar and date format
        calendar = Calendar.getInstance();
        dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

        // Initialize UI elements
        initializeUI();

        // Load administrators
        loadAdministrators();

        // Set up time slots spinner
        setupTimeSlotSpinner();
    }

    private void initializeUI() {
        Log.d(TAG, "initializeUI: Finding UI elements");

        try {
            spinnerAdmin = (Spinner) findViewById(R.id.spinnerAdmin);
            btnSelectDate = (Button) findViewById(R.id.btnSelectDate);
            tvSelectedDate = (TextView) findViewById(R.id.tvSelectedDate);
            spinnerTimeSlot = (Spinner) findViewById(R.id.spinnerTimeSlot);
            btnAddTimeSlot = (Button) findViewById(R.id.btnAddTimeSlot);
            listViewTimeSlots = (ListView) findViewById(R.id.listViewTimeSlots);

            // Set up date selection
            btnSelectDate.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showDatePickerDialog();
                }
            });

            // Set up admin selection
            spinnerAdmin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    Log.d(TAG, "onItemSelected: Admin selected with ID: " + id);
                    selectedAdminId = (int) id;
                    updateTimeSlotsList();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    selectedAdminId = -1;
                }
            });

            // Set up add time slot button
            btnAddTimeSlot.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    addTimeSlot();
                }
            });

            // Set up time slot list for deletion
            listViewTimeSlots.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    promptDeleteTimeSlot((int) id);
                }
            });

            Log.d(TAG, "initializeUI: UI elements initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "initializeUI: Error initializing UI elements", e);
            Toast.makeText(this, "Error initializing UI: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setupTimeSlotSpinner() {
        Log.d(TAG, "setupTimeSlotSpinner: Setting up time slot spinner");

        try {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    this,
                    android.R.layout.simple_spinner_dropdown_item,
                    timeSlots
            );

            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerTimeSlot.setAdapter(adapter);
        } catch (Exception e) {
            Log.e(TAG, "setupTimeSlotSpinner: Error setting up time slot spinner", e);
        }
    }

    private void loadAdministrators() {
        Log.d(TAG, "loadAdministrators: Loading administrators from database");

        try {
            Cursor cursor = dbManager.getAllAdministrators();

            if (cursor != null && cursor.getCount() > 0) {
                Log.d(TAG, "loadAdministrators: Found " + cursor.getCount() + " administrators");

                // Create adapter with administrator names
                SimpleCursorAdapter adapter = new SimpleCursorAdapter(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        cursor,
                        new String[]{"name"},
                        new int[]{android.R.id.text1},
                        0
                );

                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerAdmin.setAdapter(adapter);
            } else {
                Log.w(TAG, "loadAdministrators: No administrators found in database");

                // Handle case where no administrators exist
                List<String> defaultList = new ArrayList<>();
                defaultList.add("No administrators found");

                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        defaultList
                );

                spinnerAdmin.setAdapter(adapter);
                btnSelectDate.setEnabled(false);
                btnAddTimeSlot.setEnabled(false);
            }
        } catch (Exception e) {
            Log.e(TAG, "loadAdministrators: Error loading administrators", e);
            Toast.makeText(this, "Error loading administrators: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showDatePickerDialog() {
        Log.d(TAG, "showDatePickerDialog: Showing date picker dialog");

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                        calendar.set(Calendar.YEAR, year);
                        calendar.set(Calendar.MONTH, month);
                        calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                        selectedDate = dateFormat.format(calendar.getTime());
                        tvSelectedDate.setText("Date: " + selectedDate);

                        Log.d(TAG, "onDateSet: Date selected: " + selectedDate);

                        // Refresh the time slots list
                        updateTimeSlotsList();
                    }
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        // Set minimum date to today
        datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);

        datePickerDialog.show();
    }

    private void updateTimeSlotsList() {
        if (selectedAdminId == -1 || selectedDate == null) {
            Log.d(TAG, "updateTimeSlotsList: Admin ID or date not selected yet");
            return;
        }

        Log.d(TAG, "updateTimeSlotsList: Updating time slots for admin " + selectedAdminId +
                " on date " + selectedDate);

        try {
            Cursor cursor = dbManager.getAdminTimeSlots(selectedAdminId, selectedDate);

            if (cursor != null && cursor.getCount() > 0) {
                Log.d(TAG, "updateTimeSlotsList: Found " + cursor.getCount() + " time slots");

                String[] fromColumns = {"time_slot", "status"};
                int[] toViews = {android.R.id.text1, android.R.id.text2};

                SimpleCursorAdapter adapter = new SimpleCursorAdapter(
                        this,
                        android.R.layout.simple_list_item_2,
                        cursor,
                        fromColumns,
                        toViews,
                        0
                );

                listViewTimeSlots.setAdapter(adapter);
            } else {
                Log.d(TAG, "updateTimeSlotsList: No time slots found, setting empty adapter");

                // No time slots yet, set empty adapter
                listViewTimeSlots.setAdapter(null);
                Toast.makeText(this, "No time slots found for this date", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "updateTimeSlotsList: Error updating time slots list", e);
            Toast.makeText(this, "Error loading time slots: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void addTimeSlot() {
        if (selectedAdminId == -1) {
            Toast.makeText(this, "Please select an administrator", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedDate == null) {
            Toast.makeText(this, "Please select a date", Toast.LENGTH_SHORT).show();
            return;
        }

        String timeSlot = spinnerTimeSlot.getSelectedItem().toString();
        Log.d(TAG, "addTimeSlot: Adding time slot " + timeSlot + " for admin " + selectedAdminId +
                " on date " + selectedDate);

        // Check if time slot already exists
        List<String> existingSlots = dbManager.getExistingTimeSlots(selectedAdminId, selectedDate);
        if (existingSlots.contains(timeSlot)) {
            Toast.makeText(this, "This time slot already exists", Toast.LENGTH_SHORT).show();
            return;
        }

        // Add the time slot
        boolean success = dbManager.addAdminAvailability(selectedAdminId, selectedDate, timeSlot);

        if (success) {
            Toast.makeText(this, "Time slot added successfully", Toast.LENGTH_SHORT).show();
            updateTimeSlotsList(); // Refresh list
        } else {
            Toast.makeText(this, "Failed to add time slot", Toast.LENGTH_SHORT).show();
        }
    }

    private void promptDeleteTimeSlot(final int timeSlotId) {
        Log.d(TAG, "promptDeleteTimeSlot: Prompting to delete time slot with ID: " + timeSlotId);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Delete Time Slot");
        builder.setMessage("Are you sure you want to delete this time slot?");

        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                deleteTimeSlot(timeSlotId);
            }
        });

        builder.setNegativeButton("No", null);

        builder.create().show();
    }

    private void deleteTimeSlot(int timeSlotId) {
        Log.d(TAG, "deleteTimeSlot: Deleting time slot with ID: " + timeSlotId);

        boolean deleted = dbManager.deleteTimeSlot(timeSlotId);

        if (deleted) {
            Toast.makeText(this, "Time slot deleted", Toast.LENGTH_SHORT).show();
            updateTimeSlotsList();
        } else {
            Toast.makeText(this, "Cannot delete booked time slots", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        // Release references but don't close connections
        dbManager = null;

        // Rest of your cleanup code
        super.onDestroy();
    }
}