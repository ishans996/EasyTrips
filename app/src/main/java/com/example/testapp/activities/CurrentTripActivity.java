package com.example.testapp.activities;

import android.animation.ArgbEvaluator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager.widget.ViewPager;

import com.example.testapp.R;
import com.example.testapp.adapters.CurrentTripAdapter;
import com.example.testapp.models.ExploreModel;
import com.example.testapp.utils.MapsDataParser;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.RectangularBounds;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.AutocompleteActivity;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;

@RequiresApi(api = Build.VERSION_CODES.HONEYCOMB)
public class CurrentTripActivity extends AppCompatActivity {

    private static final int AUTOCOMPLETE_REQUEST_CODE = 1;
    private FirebaseAuth mAuth = FirebaseAuth.getInstance();
    FirebaseUser currentUser;

    private static final String TAG = "MainActivity";

    LatLng origin,destination;
    PlacesClient placesClient;
    ViewPager viewPager;
    CurrentTripAdapter adapter;
    List<ExploreModel> model_opt_off = new ArrayList<>();
    List<ExploreModel> model_opt_on =  new ArrayList<>();
    Integer[] colors = null;
    ArgbEvaluator argbEvaluator = new ArgbEvaluator();
    Button route,editBtn,doneBtn,customStopBtn;
    Switch opt_switch;
    Boolean optimization = false;
    LinearLayout removeItem;
    ImageView empty_trip;
    BottomNavigationView navigation;
    String opt_off,opt_on;
//    LinkedHashMap<Integer, HashMap<String,String>> trip_data = new LinkedHashMap<>();
    LinkedHashMap<String, ExploreModel> data_models_map = new LinkedHashMap<>();
    ArrayList<Integer> waypoint_order = new ArrayList<>();
    ArrayList<String> saved_api_ids = new ArrayList<>();
    Boolean same,somethingDeleted = false, customStopAdded = false; //checks if waypoint_order is same for both non optimized and optimized state
    ProgressBar progressBar;

    SharedPreferences sharedPref;
    SharedPreferences.Editor editor;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(R.anim.fadein, R.anim.fadeout);
        setContentView(R.layout.activity_current_trip);

        loadTripData();

        Intent i = getIntent();
        origin = i.getParcelableExtra("origin");
        destination = i.getParcelableExtra("destination");
        sharedPref = getApplicationContext().getSharedPreferences(
                getString(R.string.shared_pref_file_name), Context.MODE_PRIVATE);
        editor = sharedPref.edit();

        opt_switch = findViewById(R.id.optimize_switch);
        route = findViewById(R.id.showRoute);
        viewPager = findViewById(R.id.viewPager);
        removeItem = findViewById(R.id.removeItemFromTrip);
        editBtn = findViewById(R.id.editBtn);
        doneBtn = findViewById(R.id.doneBtn);
        customStopBtn = findViewById(R.id.customStop);
        progressBar = findViewById(R.id.curr_trip_progress);

        progressBar.getIndeterminateDrawable().setColorFilter(getResources().getColor(R.color.colorDark),
                android.graphics.PorterDuff.Mode.MULTIPLY);
        setViewPagerBackground();

        initializePlaces();

        //show empty_trip_notification and hide everything else
        if(data_models_map.keySet().size() == 0){
            showEmptyTripUI();
            progressBar.setVisibility(View.GONE);
        }

        else{

            progressBar.setVisibility(View.VISIBLE);

            //this is essential for maps activity to work, not required when we access location in app
            setTmpLocation();

            updateModel(0);

            optimizeRoute();

        }

        bottomNavigation();

        createOnClickListeners();

    }

    private void initializePlaces() {
        String apiKey = getResources().getString(R.string.autocomplete_api_key);

        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), apiKey);
        }

        // Create a new Places client instance.
        placesClient = Places.createClient(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == AUTOCOMPLETE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Place place = Autocomplete.getPlaceFromIntent(data);
                Log.i(TAG, "Place: " + place.getName() + ", " + place.getId() + ", " + place.getLatLng());
                //Toast.makeText(AutocompleteFromIntentActivity.this, "ID: " + place.getId() + "address:" + place.getAddress() + "Name:" + place.getName() + " latlong: " + place.getLatLng(), Toast.LENGTH_LONG).show();
                String title = place.getName();
                LatLng customLoc = place.getLatLng();
                String lat = String.valueOf(customLoc.latitude);
                String lon = String.valueOf(customLoc.longitude);
                String time = "NO ESTIMATE";

                int curr_id,position;
                optimization = false;
                customStopAdded = true;
                ExploreModel customModel = new ExploreModel(title,lat,lon,true,time);
                boolean was_checked = opt_switch.isChecked();
                opt_switch.setChecked(false);

                Log.d(TAG,"after adding custom place ");

                if(!model_opt_off.isEmpty()){
                    position = viewPager.getCurrentItem();
                    if(!was_checked){
                        curr_id = model_opt_off.get(position).getId();
                        model_opt_off.add(position,customModel);

                        for(ExploreModel m:model_opt_off){
                            Log.d(TAG,m.getTitle());
                        }

                    }else{
                        curr_id = model_opt_on.get(position).getId();
                        model_opt_on.add(position,customModel);

                        for(ExploreModel m:model_opt_on){
                            Log.d(TAG,m.getTitle());
                        }

                    }

                    updateModel(position);
                }

                else{
                    model_opt_off.add(0,customModel);
                    updateModel(0);
                }

                Log.d("inserting:",""+viewPager.getCurrentItem());

                //data_models_map.remove(curr_id + "");
                saveTripData();

                // do query with address

            } else if (resultCode == AutocompleteActivity.RESULT_ERROR) {
                // TODO: Handle the error.
                Status status = Autocomplete.getStatusFromIntent(data);
                //Toast.makeText(AutocompleteFromIntentActivity.this, "Error: " + status.getStatusMessage(), Toast.LENGTH_LONG).show();
                Log.i(TAG, status.getStatusMessage());
            } else if (resultCode == RESULT_CANCELED) {
                // The user canceled the operation.
            }
        }
    }

    public void onSearchCalled() {
        // Set the fields to specify which types of place data to return.
        List<Place.Field> fields = Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG);
        // Start the autocomplete intent.

        //these are bounds for delhi -> update these bounds in firestore database
        LatLng northEast = new LatLng(28.847711, 77.341943);
        LatLng southWest = new LatLng(28.476807, 76.958110);

        Intent intent = new Autocomplete.IntentBuilder(
                AutocompleteActivityMode.OVERLAY, fields).setCountry("IN")  //INDIA
                .setHint("Stops within Delhi")
                .setLocationRestriction(RectangularBounds.newInstance(
                        southWest,northEast))
                .build(this);
        startActivityForResult(intent, AUTOCOMPLETE_REQUEST_CODE);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void createOnClickListeners(){

        customStopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(getBaseContext(),"not working now! Oops ",Toast.LENGTH_SHORT).show();
                //onSearchCalled();
            }
        });

        route.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(data_models_map.keySet().size() > 0){

                    Intent intent = new Intent(getBaseContext(), MapsActivity.class);
                    intent.putExtra("optimization",optimization);
                    Log.d("origin before",origin+"");
                    intent.putExtra("origin",origin);
                    Log.d(TAG,"origin sent to Maps Activity is " + origin);
                    intent.putExtra("destination",destination);
                    intent.putExtra("waypoints",waypoint_order);
                    intent.putExtra("same",same);
                    startActivity(intent);

                }

            }
        });

        editBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                editBtn.setVisibility(View.GONE);
                doneBtn.setVisibility(View.VISIBLE);
                removeItem.setVisibility(View.VISIBLE);
                route.setVisibility(View.GONE);
                opt_switch.setVisibility(View.GONE);
            }
        });

        doneBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                editBtn.setVisibility(View.VISIBLE);
                doneBtn.setVisibility(View.GONE);
                removeItem.setVisibility(View.GONE);
                route.setVisibility(View.VISIBLE);
                opt_switch.setVisibility(View.VISIBLE);

                if(data_models_map.keySet().size() >= 1 && (somethingDeleted || customStopAdded)) {
                    opt_off = opt_on = "";
                    optimizeRoute();
                    somethingDeleted = false;
                    customStopAdded = false;
                }

            }
        });


        removeItem.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                if(event.getAction() == MotionEvent.ACTION_DOWN){
                    removeItem.setBackgroundColor(getResources().getColor(R.color.light_red));
                    return true;
                }
                if(event.getAction() == MotionEvent.ACTION_UP){
                    v.performClick();
                    removeItem.setBackgroundColor(0x00000000);
                    loadTripData();
                    somethingDeleted = true;
                    boolean was_checked = opt_switch.isChecked();
                    opt_switch.setChecked(false); //because it may not be optimized.
                    removeFromModel(was_checked);
                    optimization = false;
                    saveTripData();
                    if(data_models_map.keySet().size() == 0){
                        showEmptyTripUI();
                    }

                    return true;
                }
                return false;
            }
        });

        opt_switch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                optimization = opt_switch.isChecked();

                if(optimization){
                    if(data_models_map.keySet().size() > 1){
                        loadApiResult(optimization);
                        //opt_on has response //create new model as model_opt_on
                        JSONObject jObject;
                        try {
                            jObject = new JSONObject(opt_on);
                            MapsDataParser parser = new MapsDataParser(jObject);
                            waypoint_order = parser.get_waypoint_order();
                            Log.d("waypoints ",waypoint_order+"");
                            if(waypoint_order.size() == data_models_map.keySet().size()){
                                same = true;
                                ArrayList<Integer> trip_data_array = new ArrayList<>(waypoint_order.size());
                                for(int i = 0; i < waypoint_order.size(); i++){
                                    trip_data_array.add(i);
                                }
                                Log.d(TAG, "check toast " + trip_data_array + "----" + waypoint_order);
                                for(int i = 0; i < waypoint_order.size(); i++){
                                    if(waypoint_order.get(i) != trip_data_array.get(i)){
                                        same = false;
                                    }
                                }
                                if(same){
                                    Toast.makeText(CurrentTripActivity.this,
                                            "Trip Already Optimized", Toast.LENGTH_SHORT).show();
                                }else{
                                    Toast.makeText(CurrentTripActivity.this,
                                            "Optimized", Toast.LENGTH_SHORT).show();
                                    applyModel_opt_on();
                                }
                            }


                        }catch (Exception e){e.printStackTrace();}
                    }else{
                        Toast.makeText(getBaseContext(),"No further optimization",Toast.LENGTH_SHORT).show();
                    }
                }else{
                    if(data_models_map.keySet().size() > 1) {
                        updateModel(0);
                    }
                }

            }
        });

    }

    private void showEmptyTripUI() {
        empty_trip = findViewById(R.id.empty_trip_notify);
        empty_trip.setVisibility(View.VISIBLE);
        viewPager.setVisibility(View.GONE);
        opt_switch.setVisibility(View.GONE);
        removeItem.setVisibility(View.GONE);
        route.setVisibility(View.GONE);
        editBtn.setVisibility(View.GONE);
        doneBtn.setVisibility(View.GONE);
    }

    private void applyModel_opt_on() {
        // waypoint (0,3,1,2)    (0,1,2,3)
        model_opt_on = new ArrayList<>();
        for(int index:waypoint_order){
            model_opt_on.add(model_opt_off.get(index));
        }
        updateModel(0);
    }

    private void optimizeRoute() {

        ArrayList<Integer> temp = new ArrayList<>();
        for(String s: data_models_map.keySet()){
            // Check if string contains only numbers
            if(s.matches("^[0-9]+$")) {
                temp.add(Integer.parseInt(s));
            }
        }
        Collections.sort(temp);
        String shared_pref_ids = sharedPref.getString("saved_api_ids","");
        Log.d(TAG, "optimizeRoute: " + temp);
        Log.d(TAG, "optimizeRoute: " + shared_pref_ids);
        if(temp.toString().equals(shared_pref_ids))
            return;

        StringBuffer waypoints_coordinates ;
        waypoints_coordinates = getWaypointsCoordinates();

        setTmpLocation();

        Log.d(TAG, "optimizeRoute: before");
        String url_opt_is_false = getUrl(origin,destination,false,waypoints_coordinates);
        Log.d(TAG, "optimizeRoute: url " + url_opt_is_false);
        String url_opt_is_true = getUrl(origin,destination,true,waypoints_coordinates);

        DownloadTask task_opt_is_false = new DownloadTask();
        task_opt_is_false.execute(url_opt_is_false);
        DownloadTask task_opt_is_true = new DownloadTask();
        task_opt_is_true.execute((url_opt_is_true));

        try {
            opt_off = task_opt_is_false.get();
            saveApiResult(false);

            opt_on = task_opt_is_true.get();
            saveApiResult(true);

        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }



    private void setViewPagerBackground() {

        Integer[] colors_temp = {
                getResources().getColor(R.color.color7),
                getResources().getColor(R.color.color9),
                getResources().getColor(R.color.color10),
                getResources().getColor(R.color.color11),
                getResources().getColor(R.color.color12),
                getResources().getColor(R.color.color13),
                getResources().getColor(R.color.color14),
                getResources().getColor(R.color.color15),
                getResources().getColor(R.color.color16)
        };

        colors = colors_temp;

        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

                if(adapter != null){

                    if (position < (adapter.getCount() -1) && position < (colors.length - 1)) {
                        viewPager.setBackgroundColor(

                                (Integer) argbEvaluator.evaluate(
                                        positionOffset,
                                        colors[position],
                                        colors[position + 1]
                                )
                        );
                    }

                    else {
                        viewPager.setBackgroundColor(colors[colors.length - 1]);
                    }

                }

            }

            @Override
            public void onPageSelected(int position) {
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }

        });
    }

//    private HashMap<Integer, DocumentReference> CreateDocReference(String... cities) {
//        FirebaseFirestore db = FirebaseFirestore.getInstance();
//        DocumentReference city_reference = db.collection("ts_data").document(cities[0]);
//        HashMap<Integer, DocumentReference> tourist_places = new HashMap<>();
//
//        for (int id : trip_data.keySet()) {
//            String tourist_places_id = cities[0] + "::" + id;
//            tourist_places.put(id,city_reference.collection(cities[0] + "_data").document(tourist_places_id));
//        }
//        return tourist_places;
//    }

//    private void createStorageReference(String... cities) {
//
////        HashMap<Integer, DocumentReference> tp = CreateDocReference(cities);
//        final StorageReference storageReference = FirebaseStorage.getInstance().getReference();
//
//        for(final int id : trip_data.keySet()){
//            Log.d("proper id is",id+"");
//
//            tp.get(id).get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
//
//                @Override
//                public void onSuccess(DocumentSnapshot document) {
//
//                    assert document != null;
//                    if (!document.exists()) {
//                        Log.d("existence: ", "No such document");
//                    } else {
//                        String img_name = "" + document.get("image_name");
//                        Log.d("img_name is ",img_name);
//                        final String title = "" + document.get("title");
//                        final String time_to_cover = "" + document.get("duration_required_to_visit");
//                        String[] parts = time_to_cover.split(":",2);
//                        int hour = Integer.parseInt(parts[0]);
//                        int min = Integer.parseInt(parts[1]);
//                        final String tot_time;
//                        if(hour > 0 && min > 0){
//                            tot_time = hour + " hrs " + min + " min";
//                        }
//                        else if (min > 0 && hour == 0){
//                            tot_time = min + " min";
//                        }
//                        else if(hour > 0 && min == 0){
//                            tot_time = hour + " hrs";
//                        }else {
//                            tot_time = "no estimate";
//                        }
//                        StorageReference spaceRef  = storageReference.child("photos_delhi/" + img_name);
//                        Log.d("spaceRef is",spaceRef.getName());
//                        spaceRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
//                            @Override
//                            public void onSuccess(Uri uri) {
//                                addTo_Model_opt_off(uri,title,tot_time,id);
//                            }
//                        });
//                    }
//                }
//            });
//        }
//    }

    public void removeFromModel(boolean was_checked) {
        int curr_id;
        if(data_models_map.isEmpty()){
            Toast.makeText(getBaseContext(),"Trip is Empty",Toast.LENGTH_SHORT).show();
        }else{

            int position = viewPager.getCurrentItem();
            if(!was_checked){
                curr_id = model_opt_off.get(position).getId();
                model_opt_off.remove(position);
            }else{
                curr_id = model_opt_on.get(position).getId();
                model_opt_on.remove(position);
                for(int i = 0; i < model_opt_off.size(); i++){
                    if(model_opt_off.get(i).getId() == curr_id){
                        model_opt_off.remove(i);
                        break;
                    }
                }
            }

            Log.d("deleting:",""+position);
            data_models_map.remove(curr_id + "");
            updateModel(position);
        }

    }

    private void updateModel(int start_position) {
        Log.d(TAG,"updateModel is called ");
        if(!optimization)   adapter = new CurrentTripAdapter(model_opt_off, this);
        else                adapter = new CurrentTripAdapter(model_opt_on , this);

        viewPager.setAdapter(adapter);
        adapter.notifyDataSetChanged();
        viewPager.setCurrentItem(start_position);
        viewPager.setPadding(100, 0, 100, 0);
        progressBar.setVisibility(View.GONE);
    }

//    private void addTo_Model_opt_off(Uri result, String title, String tot_time, int id) {
//
//        /*model_opt_off.set(index,new ExploreModel(result,title,short_description,id));
//
//       */
//        model_opt_off.add(new ExploreModel(result,title,tot_time,id));
//
//        if(model_opt_off.size() == trip_data.keySet().size()){
//            int size = model_opt_off.size();
//            ArrayList<Integer> ids = new ArrayList<>(trip_data.keySet());
//            List<ExploreModel> proper_model = new ArrayList<>();
//            for(int i=0;i<size;i++){
//                proper_model.add(new ExploreModel());
//            }
//            for(int i=0;i<size;i++){
//                ExploreModel curr_model = model_opt_off.get(i);
//                int curr_id = model_opt_off.get(i).getId();
//                Log.d("curr_id is ",curr_id+"");
//                int index = ids.indexOf(curr_id);
//                proper_model.set(index,curr_model);
//            }
//            model_opt_off = proper_model;
//            updateModel(0);
//        }
//
//    }

    private String getUrl(LatLng origin, LatLng dest, Boolean opt,StringBuffer waypoints_coordinates) {

        //Directions API URL
        String directions_api = "https://maps.googleapis.com/maps/api/directions/";

        // Origin of route
        String str_origin = "origin=" + origin.latitude + "," + origin.longitude;

        // Destination of route
        String str_dest = "destination=" + dest.latitude + "," + dest.longitude;

        // Sensor enabled
        String sensor = "sensor=true";

        String waypoints = waypoints = "waypoints=optimize:" + opt + waypoints_coordinates.toString();

        // Building the parameters to the web service
        String parameters = str_origin + "&" + str_dest + "&" + sensor + "&" + waypoints;

        // Output format
        String output = "json";
        //API KEY
        String apiKey ="key="+getResources().getString(R.string.directions_api_key );
        Log.d(TAG, "getUrl: " + apiKey);

        //url
        String url = directions_api+output+"?"+parameters+"&"+apiKey+"\n";
        Log.d("url is ",url);
        return url;
    }

    private class DownloadTask extends AsyncTask<String,Integer,String> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected String doInBackground(String... urls) {
            URL url;
            StringBuilder result = new StringBuilder();
            try {
                url = new URL(urls[0]);
                InputStream in = url.openStream();
                InputStreamReader reader = new InputStreamReader(in);
                char[] buffer = new char[1024];
                int bytesRead = reader.read(buffer);
                while(bytesRead != -1){

                    result.append(buffer,0,bytesRead);
                    bytesRead = reader.read(buffer);

                }
                return result.toString();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return "not possible";
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            Log.d(TAG,"api_result is "+result);
        }
    }

    private StringBuffer getWaypointsCoordinates() {

        StringBuffer waypoints_coordinates = new StringBuffer("");

        for(String id:data_models_map.keySet()){

            String lon = data_models_map.get(id).getLon();
            String lat = data_models_map.get(id).getLat();

            waypoints_coordinates.append("|").append(lat).append(",").append(lon);

        }
        Log.d("waypoint is ",waypoints_coordinates+"");
        return waypoints_coordinates;
    }

    private void setTmpLocation() {
        double hotel_lat = 28.651685;
        double hotel_lon = 77.217220;

        LatLng tmp_origin,tmp_destination;
        tmp_origin = new LatLng(hotel_lat,hotel_lon);
        tmp_destination = tmp_origin;

        //change this
        origin = tmp_origin;
        Log.d(TAG,"origin here "+origin);
        destination = tmp_destination;
    }

    private void saveApiResult(Boolean opt){
        if(!opt){
            try {
                File file = new File(getDir("apiResponse", MODE_PRIVATE), "opt_false");
                ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(file));
                outputStream.writeObject(opt_off);
                outputStream.flush();
                outputStream.close();
                for(String i: data_models_map.keySet()) {
                    saved_api_ids.add(i);
                };
                Collections.sort(saved_api_ids);
                editor.putString("saved_api_ids", saved_api_ids.toString());
                editor.commit();

            }catch (IOException e){
                e.printStackTrace();
            }
        }else{
            try {
                File file = new File(getDir("apiResponse", MODE_PRIVATE), "opt_true");
                ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(file));
                outputStream.writeObject(opt_on);
                outputStream.flush();
                outputStream.close();
            }catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    private void loadApiResult(Boolean opt) {
        if (opt) {
            try {
                File file = new File(getDir("apiResponse", MODE_PRIVATE), "opt_true");
                ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
                opt_on = (String) ois.readObject();

            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                opt_on = "";
            }
        }else{
            try {
                File file = new File(getDir("apiResponse", MODE_PRIVATE), "opt_false");
                ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
                opt_off = (String) ois.readObject();

            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                opt_off = "";
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        currentUser = mAuth.getCurrentUser();
        loadTripData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTripData();
    }

    private void saveTripData(){
        try {
            File file = new File(getDir("data", MODE_PRIVATE), "data_models_map");
            ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(file));
            outputStream.writeObject(data_models_map);
            outputStream.flush();
            outputStream.close();

            for(ExploreModel model: model_opt_off){
                Log.d(TAG, "saveTripData: " + model.getId() +  " " + model.getTitle());
            }

        }catch (IOException e){
            e.printStackTrace();
        }

    }

    private void loadTripData() {
        try {
            File file = new File(getDir("data", MODE_PRIVATE), "data_models_map");
            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
            data_models_map = (LinkedHashMap) ois.readObject();

            model_opt_off.clear();

            for(String s: data_models_map.keySet()){
                model_opt_off.add(data_models_map.get(s));
            }
            for(ExploreModel model: model_opt_off){
                Log.d(TAG, "loadTripData: " + model.getId() +  " " + model.getTitle());
            }

        }
        catch (IOException e){
            e.printStackTrace();
        }
        catch (ClassNotFoundException e){
            data_models_map = new LinkedHashMap<>();
        }
    }

    private void bottomNavigation() {
        loadTripData();
        navigation = findViewById(R.id.navigation_bar);
        navigation.getMenu().findItem(R.id.menu_item1).setChecked(true);
        navigation.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.menu_item0:
                        Intent a = new Intent(CurrentTripActivity.this, ExploreActivity.class);
                        startActivity(a);
                        break;
                    case R.id.menu_item1:
                        break;
                    case R.id.menu_item2:
                        if(currentUser != null){
                            Intent b = new Intent(CurrentTripActivity.this, AccountActivity.class);
                            startActivity(b);
                        }
                        else {
                            Intent b = new Intent(CurrentTripActivity.this, LoginActivity.class);
                            startActivity(b);
                        }
                        break;
                }
                return false;
            }
        });
    }
}



