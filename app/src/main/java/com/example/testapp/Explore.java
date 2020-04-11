package com.example.testapp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat;

import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;

import me.ibrahimsn.lib.OnItemSelectedListener;


public class Explore extends AppCompatActivity {
    private static final String TAG = "Explore";


    private FirebaseFirestore rootRef;
    LinearLayoutManager linearLayoutManager;
    RecyclerView recyclerView;

    AnimatedVectorDrawable avd2 ;
    AnimatedVectorDrawableCompat avd;

    int BOOL_ADD_TO_TRIP = 1;
    int last_click_position;

    private ArrayList<Integer> trip_indices;
    private ExploreAdapter adapter;
    BottomNavigationView navigation;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(R.anim.fadein, R.anim.fadeout);
        setContentView(R.layout.activity_explore);
        rootRef = FirebaseFirestore.getInstance();

        loadData();
        setUpRecyclerView();
        bottomNavigation();

    }

    private void bottomNavigation() {
        navigation = (BottomNavigationView) findViewById(R.id.navigation_bar);
        navigation.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.menu_item0:
                        break;
                    case R.id.menu_item1:
                        Intent a = new Intent(Explore.this, CurrentTripActivity.class);
                        a.putExtra("trip_indices", trip_indices);
                        startActivity(a);
                        break;
                    case R.id.menu_item2:
                        Intent b = new Intent(Explore.this, AccountActivity.class);
                        startActivity(b);
                        break;
                }
                return false;
            }
        });
    }

    private void setUpRecyclerView() {
        Query query = rootRef.collection("ts_data")
                .document("delhi")
                .collection("delhi_data")
                .orderBy("priority", Query.Direction.ASCENDING);

        FirestoreRecyclerOptions<explore_model> options = new FirestoreRecyclerOptions
                .Builder<explore_model>()
                .setQuery(query, explore_model.class)
                .build();

        adapter = new ExploreAdapter(options, getResources());

        recyclerView = findViewById(R.id.recycler_view);

        linearLayoutManager = new LinearLayoutManager(getApplicationContext());
        recyclerView.setLayoutManager(linearLayoutManager);

        recyclerView.setAdapter(adapter);
        adapter.notifyDataSetChanged();

        adapter.setOnClickListener(new ExploreAdapter.OnItemClickListener() {
            @Override
            public void onViewClick(DocumentSnapshot documentSnapshot, int position) {
                last_click_position = position;
                Intent it = new Intent(Explore.this, tsDetails.class);
                it.putExtra("snapshot", documentSnapshot.toObject(explore_model.class));
                it.putExtra("click_position", position);
                if(trip_indices.contains(position+1))
                    it.putExtra("already_present_in_trip", 1);
                else
                    it.putExtra("already_present_in_trip", 0);
                startActivityForResult(it, BOOL_ADD_TO_TRIP);
            }

//            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
//            @Override
//            public void onButtonClick(int position, ImageView done) {
//                Toast.makeText(Explore.this, "Heres the position: " +
//                        position, Toast.LENGTH_SHORT).show();
//                Drawable drawable = done.getDrawable();
//
//                if(drawable instanceof AnimatedVectorDrawableCompat){
//                    avd = (AnimatedVectorDrawableCompat) drawable;
//                    avd.start();
//                }
//                else if(drawable instanceof AnimatedVectorDrawable){
//                    avd2 = (AnimatedVectorDrawable) drawable;
//                    avd2.start();
//
//                }
//
//            }

        });



    }

    private void saveData(){
        SharedPreferences sharedPreferences = getSharedPreferences("shared_preferences", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        Gson gson = new Gson();
        String json = gson.toJson(trip_indices);
        editor.putString("trip_indices", json);
        editor.apply();
    }

    private void loadData(){
        SharedPreferences sharedPreferences = getSharedPreferences("shared_preferences", MODE_PRIVATE);
        Gson gson = new Gson();
        String json = sharedPreferences.getString("trip_indices", null);
        Type type = new TypeToken<ArrayList<Integer>>() {}.getType();
        trip_indices = gson.fromJson(json, type);
        if(trip_indices == null){
            trip_indices = new ArrayList<Integer>();
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);


        if (requestCode == BOOL_ADD_TO_TRIP) {
            if (resultCode == Activity.RESULT_OK) {
                loadData();
                int add_to_trip_value = data.getIntExtra("add_to_trip_value", 0);
                if (add_to_trip_value == 1) {
                    if (!trip_indices.contains(last_click_position + 1))
                        trip_indices.add(last_click_position + 1);

                }
                saveData();
                if (resultCode == Activity.RESULT_CANCELED) {
                    //Write your code if there's no result
                }
            }
        }

    }


    @Override
    protected void onStart() {
        super.onStart();
        adapter.startListening();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (adapter != null) {
            adapter.stopListening();
        }
    }


    public void show_trip(View view) {
        Log.d(TAG, "show_trip: " + trip_indices);
    }
}
