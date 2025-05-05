package com.example.experiment;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

public class RegisterActivity extends Activity {
    EditText etNewUsername, etNewPassword, etConfirmPassword;
    RadioGroup rgUserRole;
    // Change this:
    // DatabaseHelper dbHelper;
    // To this:
    private DatabaseManager dbManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Change this:
        // dbHelper = new DatabaseHelper(this);
        // To this:
        dbManager = DatabaseManager.getInstance(this);

        etNewUsername = (EditText)findViewById(R.id.etNewUsername);
        etNewPassword = (EditText)findViewById(R.id.etNewPassword);
        etConfirmPassword = (EditText)findViewById(R.id.etConfirmPassword);
        rgUserRole = (RadioGroup) findViewById(R.id.rgUserRole);
    }

    public void registerUser(View view) {
        String username = etNewUsername.getText().toString().trim();
        String password = etNewPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get selected role (Student or Admin)
        int selectedRoleId = rgUserRole.getCheckedRadioButtonId();
        String role = (selectedRoleId == R.id.rbAdmin) ? "Admin" : "Student";

        // Change this database access code:
        /*
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("username", username);
        values.put("password", password);
        values.put("role", role);

        long result = db.insert("users", null, values);
        db.close();
        */

        // To this:
        boolean result = dbManager.registerUser(username, password, role);

        // And modify the check:
        if (result) {
            Toast.makeText(this, "Registration Successful", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        } else {
            Toast.makeText(this, "Username already exists", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        // Don't close the database manager, just release the reference
        dbManager = null;

        super.onDestroy();
    }

    public void goBackToLogin(View view) {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
