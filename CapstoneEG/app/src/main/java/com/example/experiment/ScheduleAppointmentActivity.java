package com.example.experiment;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class ScheduleAppointmentActivity extends Activity {
    private DatabaseManager dbManager;
    private int adminId;
    private String adminName;

    private TextView tvAdminInfo;
    private Button btnSelectDate;
    private ListView listViewTimeSlots;
    private EditText etReason;
    private Button btnSchedule;

    private String selectedDate;
    private String selectedTimeSlot;
    private Calendar calendar;
    private SimpleDateFormat dateFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule_appointment);

        // Get adminId from intent
        adminId = getIntent().getIntExtra("ADMIN_ID", -1);
        if (adminId == -1) {
            Toast.makeText(this, "Error: Administrator not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize views
        tvAdminInfo = (TextView) findViewById(R.id.tvAdminInfo);
        btnSelectDate = (Button) findViewById(R.id.btnSelectDate);
        listViewTimeSlots = (ListView) findViewById(R.id.listViewTimeSlots);
        etReason = (EditText) findViewById(R.id.etReason);
        btnSchedule = (Button) findViewById(R.id.btnSchedule);

        // Set up date
        calendar = Calendar.getInstance();
        dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

        // Initialize database manager
        dbManager = new DatabaseManager(this);

        // Load administrator info
        loadAdministratorInfo();

        // Set up date selection
        btnSelectDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDatePickerDialog();
            }
        });

        // Set up time slot selection
        listViewTimeSlots.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Cursor cursor = (Cursor) parent.getItemAtPosition(position);
                selectedTimeSlot = cursor.getString(cursor.getColumnIndex("time_slot"));
                Toast.makeText(ScheduleAppointmentActivity.this,
                        "Selected: " + selectedTimeSlot, Toast.LENGTH_SHORT).show();
            }
        });

        // Set up schedule button
        btnSchedule.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scheduleAppointment();
            }
        });
    }

    private void loadAdministratorInfo() {
        Cursor cursor = dbManager.getAdministratorById(adminId);

        if (cursor != null && cursor.moveToFirst()) {
            adminName = cursor.getString(cursor.getColumnIndex("name"));
            String title = cursor.getString(cursor.getColumnIndex("title"));
            String department = cursor.getString(cursor.getColumnIndex("department"));

            tvAdminInfo.setText(adminName + "\n" + title + "\n" + department);
            cursor.close();
        } else {
            Toast.makeText(this, "Error: Could not load administrator info", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void showDatePickerDialog() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                        calendar.set(Calendar.YEAR, year);
                        calendar.set(Calendar.MONTH, month);
                        calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                        selectedDate = dateFormat.format(calendar.getTime());
                        btnSelectDate.setText("Date: " + selectedDate);

                        // Load available time slots for this date
                        loadAvailableTimeSlots();
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

    private void loadAvailableTimeSlots() {
        Cursor cursor = dbManager.getAvailableTimeSlots(adminId, selectedDate);

        if (cursor != null && cursor.getCount() > 0) {
            String[] fromColumns = {"time_slot"};
            int[] toViews = {android.R.id.text1};

            SimpleCursorAdapter adapter = new SimpleCursorAdapter(
                    this,
                    android.R.layout.simple_list_item_1,
                    cursor,
                    fromColumns,
                    toViews,
                    0
            );

            listViewTimeSlots.setAdapter(adapter);
        } else {
            Toast.makeText(this, "No available time slots for this date", Toast.LENGTH_SHORT).show();
            listViewTimeSlots.setAdapter(null);
        }
    }

    private void scheduleAppointment() {
        if (selectedDate == null || selectedTimeSlot == null) {
            Toast.makeText(this, "Please select a date and time", Toast.LENGTH_SHORT).show();
            return;
        }

        String reason = etReason.getText().toString().trim();
        if (reason.isEmpty()) {
            Toast.makeText(this, "Please enter a reason for the appointment", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get student username from shared preferences
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String username = prefs.getString("username", "");

        // Book the appointment
        boolean success = dbManager.bookAppointment(username, adminId, selectedDate, selectedTimeSlot, reason);

        if (success) {
            Toast.makeText(this, "Appointment scheduled successfully", Toast.LENGTH_LONG).show();
            finish();
        } else {
            Toast.makeText(this, "Failed to schedule appointment. Please try again.", Toast.LENGTH_SHORT).show();
        }
    }
}