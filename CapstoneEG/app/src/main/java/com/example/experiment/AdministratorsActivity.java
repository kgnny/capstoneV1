package com.example.experiment;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

public class AdministratorsActivity extends Activity {
    private DatabaseManager dbManager;
    private ListView listViewAdmins;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_administrators);

        dbManager = DatabaseManager.getInstance(this);
        listViewAdmins = (ListView) findViewById(R.id.listViewAdmins);

        // Get cursor of administrators
        Cursor cursor = dbManager.getAllAdministrators();

        // Create adapter with custom layout
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(
                this,
                R.layout.item_administrator, // Custom item layout
                cursor,
                new String[]{"name", "title", "department", "email", "phone"}, // Columns to display
                new int[]{
                        R.id.tvAdminName,
                        R.id.tvAdminTitle,
                        R.id.tvAdminDepartment,
                        R.id.tvAdminEmail,
                        R.id.tvAdminPhone
                }, // Views to put the data
                0
        );

        // Set the adapter
        listViewAdmins.setAdapter(adapter);

        // Set up click listener for scheduling appointments
        listViewAdmins.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // Pass the admin ID to the appointment scheduling activity
                Intent intent = new Intent(AdministratorsActivity.this, ScheduleAppointmentActivity.class);
                intent.putExtra("ADMIN_ID", (int) id);
                startActivity(intent);
            }
        });
    }


}