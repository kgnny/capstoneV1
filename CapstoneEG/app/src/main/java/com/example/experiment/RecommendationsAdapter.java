package com.example.experiment;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class RecommendationsAdapter extends ArrayAdapter<DatabaseManager.Recommendation> {
    private List<DatabaseManager.Recommendation> recommendations;
    private Activity context;

    public RecommendationsAdapter(Activity context, List<DatabaseManager.Recommendation> recommendations) {
        super(context, android.R.layout.simple_list_item_2, recommendations);
        this.context = context;
        this.recommendations = recommendations != null ? recommendations : new ArrayList<DatabaseManager.Recommendation>();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (position >= recommendations.size()) {
            return convertView; // Safety check
        }

        View view = convertView;
        if (view == null) {
            view = context.getLayoutInflater().inflate(android.R.layout.simple_list_item_2, parent, false);
        }

        TextView text1 = view.findViewById(android.R.id.text1);
        TextView text2 = view.findViewById(android.R.id.text2);

        DatabaseManager.Recommendation recommendation = recommendations.get(position);
        if (recommendation != null) {
            text1.setText(recommendation.recommendationInfo);
            text2.setText(recommendation.relevanceInfo);
        }

        return view;
    }
}