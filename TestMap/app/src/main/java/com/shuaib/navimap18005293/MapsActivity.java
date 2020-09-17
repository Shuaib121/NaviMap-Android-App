package com.shuaib.navimap18005293;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.directions.route.AbstractRouting;
import com.directions.route.Route;
import com.directions.route.RouteException;
import com.directions.route.Routing;
import com.directions.route.RoutingListener;
import com.shuaib.testmap.R;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PointOfInterest;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.PhotoMetadata;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.TypeFilter;
import com.google.android.libraries.places.api.net.FetchPhotoRequest;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.jetbrains.annotations.NotNull;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.OnConnectionFailedListener, GoogleMap.OnPoiClickListener, RoutingListener {

    //Static Variables
    private static final String fineLocation = Manifest.permission.ACCESS_FINE_LOCATION;
    private static final String coarseLocation = Manifest.permission.ACCESS_COARSE_LOCATION;
    private static final int locationPermissionRequestCode = 1;
    private static final float dZoom = 15f;
    private static final LatLngBounds latLngBounds = new LatLngBounds(
            new LatLng(-40, -168), new LatLng(71, 136));
    private static final String TAG  = "d";
    private static Polyline currentPolyline;
    private final static int LOCATION_REQUEST_CODE = 23;

    //Variables
    private View hiddenPanel,searchBox;
    private ImageView imagePlace;
    private TextView distanceTime;
    private Switch metric;
    private PlacesClient placesClient;
    private GoogleMap mMap;
    private Boolean locationPermissionsGranted  = false;
    private FusedLocationProviderClient mFusedLocationProviderClient;
    private int naviCheck = 0,latlngCheck = 0;
    private AbstractRouting.TravelMode travelMode;
    private FirebaseAuth fAuth;
    private FirebaseFirestore fStore;
    private String userTrips,destinationName,metricKmMi;
    protected LatLng userLatLng=null;
    protected LatLng destination=null;
    boolean locationPermission=false;

    //polyline object
    private List<Polyline> polylines=null;

    //Widgets
    private Fragment mSearchText;
    private ImageView mGps, mNvg,mDrive,mCycle,mWalk,mTrans;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        //Instantiate all views
        hiddenPanel = findViewById(R.id.hidden_panel);
        searchBox = findViewById(R.id.relLayout1);
        imagePlace = findViewById(R.id.imagePlaces);
        mGps = findViewById(R.id.ic_gps);
        mNvg = findViewById(R.id.ic_nvg);
        distanceTime = findViewById(R.id.distanceTime);
        metric = findViewById(R.id.metric);

        //Instantiate navigation widgets
        mDrive = findViewById(R.id.ic_drive);
        mCycle = findViewById(R.id.ic_cycle);
        mWalk = findViewById(R.id.ic_walk);
        mTrans = findViewById(R.id.ic_transit);

        //Set widgets to invisible, only show when navigation button is clicked
        mDrive.setVisibility(View.INVISIBLE);
        mCycle.setVisibility(View.INVISIBLE);
        mWalk.setVisibility(View.INVISIBLE);
        mTrans.setVisibility(View.INVISIBLE);

        //Instantiate firebase auth and firestore
        fAuth = FirebaseAuth.getInstance();
        fStore = FirebaseFirestore.getInstance();

        //Get permission to access user location
        GetLocationPermission();
        autoSuggestions();
        //Method to get users preferred metric from firestore
        metricCheck();

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        init();
        initMap();


    }


    //When an POI(Point of interested) marker is clicked, perform an action
    @Override
    public void onPoiClick(PointOfInterest poi) {
        // Initialize the SDK
        Places.initialize(getApplicationContext(), "AIzaSyAqP6s-lLkcSyZpb5xkHbrvpHFWu5cEJqk");

        // Create a new PlacesClient instance
        placesClient = Places.createClient(this);

        // Resets text view that shows distance and time
        distanceTime.setText("");

        //create placesClient for the POI
        FetchPlaceRequest request = FetchPlaceRequest.newInstance(poi.placeId,Arrays.asList(Place.Field.ADDRESS,Place.Field.PHOTO_METADATAS,Place.Field.LAT_LNG));
        placesClient.fetchPlace(request).addOnSuccessListener((response) -> {
            Place place = response.getPlace();
            mMap.clear();
            destinationName = place.getAddress();
            destination = new LatLng(place.getLatLng().latitude, place.getLatLng().longitude);
            moveCamera(new LatLng(place.getLatLng().latitude, place.getLatLng().longitude), dZoom);

            /*The following lines of code were going to bring up a new view showing the images of the
              POI, it works but did not look too good the way I laid it out so I excluded it from the app*/
            List<PhotoMetadata> metadata = place.getPhotoMetadatas();
            if (metadata == null || metadata.isEmpty()) {
                Log.w(TAG, "No photo metadata.");
                return;
            }
            PhotoMetadata photoMetadata = metadata.get(0);

            // Get the attribution text.
             String attributions = photoMetadata.getAttributions();

            // Create a FetchPhotoRequest.
            FetchPhotoRequest photoRequest = FetchPhotoRequest.builder(photoMetadata)
                    .setMaxWidth(640) // Optional.
                    .setMaxHeight(300) // Optional.
                    .build();

            placesClient.fetchPhoto(photoRequest).addOnSuccessListener((fetchPhotoResponse) -> {
                Bitmap bitmap = fetchPhotoResponse.getBitmap();
                imagePlace.setImageBitmap(bitmap);

            }).addOnFailureListener((exception) -> {
                if (exception instanceof ApiException) {
                    final ApiException apiException = (ApiException) exception;
                    Log.e(TAG, "Place not found: " + exception.getMessage());
                    final int statusCode = apiException.getStatusCode();
                    // TODO: Handle error with given status code.
                }
            });
        });
        //slideUpDown(hiddenPanel);
    }


    //Auto-suggests locations to the user when they try searching for an address
    private void autoSuggestions(){

        Places.initialize(getApplicationContext(), "AIzaSyAqP6s-lLkcSyZpb5xkHbrvpHFWu5cEJqk");
        placesClient = Places.createClient(this);

        // Initialize the AutocompleteSupportFragment.
        AutocompleteSupportFragment autocompleteFragment = (AutocompleteSupportFragment)
                getSupportFragmentManager().findFragmentById(R.id.autocomplete_fragment);

        autocompleteFragment.setTypeFilter(TypeFilter.ADDRESS);

        // Specify the types of place data to return.
        autocompleteFragment.setPlaceFields(Arrays.asList(Place.Field.ID, Place.Field.NAME,Place.Field.ADDRESS,Place.Field.LAT_LNG));

        // Set up a PlaceSelectionListener to handle the response.
        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(@NotNull Place place) {
                // TODO: Get info about the selected place.
                Log.i(TAG, "Place: " + place.getName() + ", " + place.getId() + ", " + place.getAddress());
                destination = new LatLng(place.getLatLng().latitude, place.getLatLng().longitude);
                destinationName = place.getAddress();
                moveCamera(new LatLng(place.getLatLng().latitude, place.getLatLng().longitude), dZoom);
            }

            @Override
            public void onError(@NotNull Status status) {
                // TODO: Handle the error.
                Log.i(TAG, "An error occurred: " + status);
            }
        });
    }



    // Obtain the SupportMapFragment and get notified when the map is ready to be used.
    private void initMap(){
        init();
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    //Method to set listeners for widgest/buttons
    private void init() {

        getDeviceLocation();
        //When clicked, will take user back to their current location on the map
        mGps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getDeviceLocation();
            }
        });

        //When clicked, displays different options of navigation to be used
        mNvg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(naviCheck == 0) {
                    mDrive.setVisibility(View.VISIBLE);
                    mCycle.setVisibility(View.VISIBLE);
                    mWalk.setVisibility(View.VISIBLE);
                    mTrans.setVisibility(View.VISIBLE);
                    naviCheck++;
                }
                else{
                    mDrive.setVisibility(View.INVISIBLE);
                    mCycle.setVisibility(View.INVISIBLE);
                    mWalk.setVisibility(View.INVISIBLE);
                    mTrans.setVisibility(View.INVISIBLE);
                    naviCheck--;
                }
            }
        });

        //Shows driving path
        mDrive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mMap.clear();
                travelMode = AbstractRouting.TravelMode.DRIVING;
                Findroutes(userLatLng,destination,travelMode);
            }
        });

        //Shows cycling path
        mCycle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mMap.clear();
                travelMode = AbstractRouting.TravelMode.BIKING;
                Findroutes(userLatLng,destination, travelMode);
            }
        });

        //Shows walking path
        mWalk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mMap.clear();
                travelMode = AbstractRouting.TravelMode.WALKING;
                Findroutes(userLatLng,destination, travelMode);
            }
        });

        //Shows transit park(Not too sure if this one works, seems to be the same as the walk path)
        mTrans.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mMap.clear();
                travelMode = AbstractRouting.TravelMode.TRANSIT;
                Findroutes(userLatLng,destination, travelMode);
            }
        });

        //When clicked, changes the metric system to either km or miles
        metric.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                metricChange();
            }
        });

    }


    //Method to initialize and setup the google maps fragment
    @Override
    public void onMapReady(GoogleMap googleMap) {
        Toast.makeText(this, "Map is Ready", Toast.LENGTH_SHORT).show();
        mMap = googleMap;
        mMap.setOnPoiClickListener(this);

        if (locationPermissionsGranted) {
            getDeviceLocation();

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(false);

            init();

        }
    }

    //Gets permission from user to access their current location
    private void GetLocationPermission(){

        String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION};

        if(ContextCompat.checkSelfPermission(this.getApplicationContext(), fineLocation) == PackageManager.PERMISSION_GRANTED){
            if(ContextCompat.checkSelfPermission(this.getApplicationContext(), coarseLocation) == PackageManager.PERMISSION_GRANTED){
                locationPermissionsGranted = true;
                init();
                initMap();
            }else{
                ActivityCompat.requestPermissions(this, permissions, locationPermissionRequestCode);
            }
        }else{
            ActivityCompat.requestPermissions(this, permissions, locationPermissionRequestCode);
        }
    }

    //Gets the users current location
    private void getDeviceLocation(){
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        try{
            if(locationPermissionsGranted){

                final Task location = mFusedLocationProviderClient.getLastLocation();
                location.addOnCompleteListener(new OnCompleteListener() {
                    @Override
                    public void onComplete(@NonNull Task task) {
                        if(task.isSuccessful()){
                            Location currentLocation = (Location) task.getResult();

                            moveCamera(new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude()),
                                    dZoom);

                            userLatLng = new LatLng(currentLocation.getLatitude(),currentLocation.getLongitude());

                        }else{
                            Toast.makeText(MapsActivity.this, "unable to get current location", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }catch (SecurityException e){
        }
    }


    //This brought up the view to show location image, currently not in use
    public void slideUpDown(final View view) {
        if (!isPanelShown()) {
            // Show the panel
            Animation bottomUp = AnimationUtils.loadAnimation(this,
                    R.anim.bottom_up);

            hiddenPanel.startAnimation(bottomUp);
            hiddenPanel.setVisibility(View.VISIBLE);
            searchBox.setVisibility((View.GONE));
            mGps.setVisibility((View.GONE));

        }
        else {
            // Hide the Panel
            Animation bottomDown = AnimationUtils.loadAnimation(this,
                    R.anim.bottom_down);

            hiddenPanel.startAnimation(bottomDown);
            hiddenPanel.setVisibility(View.GONE);
            searchBox.setVisibility((View.VISIBLE));
            mGps.setVisibility((View.VISIBLE));

        }
    }

    //Checked if the image view was visible or not
    private boolean isPanelShown() {
        return hiddenPanel.getVisibility() == View.VISIBLE;
    }


    //Moved the camera to the selected address which the user selected or from a POI they clicked
    private void moveCamera(LatLng latLng, float zoom){
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom));
        Log.i(TAG, "camera moved ");
        //mMap.clear();

        MarkerOptions options = new MarkerOptions().position(latLng);
        mMap.addMarker(options);
        /*if(!title.equals("My Location")){
            MarkerOptions options = new MarkerOptions().position(latLng).title(title);
            mMap.addMarker(options);
        }*/
        hideSoftKeyboard();
    }

    private void hideSoftKeyboard(){
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }


    //Was a different method I was thinking of using to get user location, for now I am using the other method
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        locationPermissionsGranted = false;

        switch(requestCode){
            case locationPermissionRequestCode:{
                if(grantResults.length > 0){
                    for(int i = 0; i < grantResults.length; i++){
                        if(grantResults[i] != PackageManager.PERMISSION_GRANTED){
                            locationPermissionsGranted = false;
                            return;
                        }
                    }
                    locationPermissionsGranted = true;
                    //initialize our map
                    initMap();
                }
            }
        }
    }

    /*Method to find the routed using the latlng and a travel mode
      This method is used from https://github.com/jd-alexander/Google-Directions-Android
      and is implemented as a dependency

    License:
    Copyright (c) 2013 Joel Dean
    Permission is hereby granted, free of charge, to any person obtaining a copy of this software
    and associated documentation files (the "Software"), to deal in the Software without
    restriction, including without limitation the rights to use, copy, modify, merge, publish,
    distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
    Software is furnished to do so, subject to the following conditions:
    The above copyright notice and this permission notice shall be included in all copies or
    substantial portions of the Software.
    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
    BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
    NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
    DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
     OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

     https://github.com/jd-alexander/Google-Directions-Android/blob/master/LICENSE.md
     */

    public void Findroutes(LatLng Start, LatLng End, AbstractRouting.TravelMode travelMode)
    {
        if(Start==null || End==null) {
            Toast.makeText(MapsActivity.this,"Invalid location",Toast.LENGTH_LONG).show();
        }
        else
        {


            Routing routing = new Routing.Builder()
                    .travelMode(travelMode)
                    .withListener(this)
                    .alternativeRoutes(true)
                    .waypoints(Start, End)
                    .key("AIzaSyAqP6s-lLkcSyZpb5xkHbrvpHFWu5cEJqk")  //also define your api key here.
                    .build();
            routing.execute();
        }
    }

    //Routing call back functions.
    @Override
    public void onRoutingFailure(RouteException e) {
        View parentLayout = findViewById(android.R.id.content);
        Snackbar snackbar= Snackbar.make(parentLayout, e.toString(), Snackbar.LENGTH_LONG);
        snackbar.show();
    }


    @Override
    public void onRoutingStart() {
        Toast.makeText(MapsActivity.this,"Finding Route...",Toast.LENGTH_LONG).show();
    }


    //If Route finding is successful
    @Override
    public void onRoutingSuccess(ArrayList<Route> route, int shortestRouteIndex) {

        //Check the metric of the user to display distance and time to them
        metricCheck();
        CameraUpdate center = CameraUpdateFactory.newLatLng(userLatLng);
        CameraUpdate zoom = CameraUpdateFactory.zoomTo(16);
        if(polylines!=null) {
            polylines.clear();
        }
        PolylineOptions polyOptions = new PolylineOptions();
        LatLng polylineStartLatLng=null;
        LatLng polylineEndLatLng=null;

        polylines = new ArrayList<>();
        //add route(s) to the map using polyline
        for (int i = 0; i <route.size(); i++) {

            if(i==shortestRouteIndex)
            {
                polyOptions.color(getResources().getColor(R.color.colorPrimary));
                polyOptions.width(7);
                polyOptions.addAll(route.get(shortestRouteIndex).getPoints());
                Polyline polyline = mMap.addPolyline(polyOptions);
                polylineStartLatLng=polyline.getPoints().get(0);
                int k=polyline.getPoints().size();
                polylineEndLatLng=polyline.getPoints().get(k-1);
                polylines.add(polyline);

                //Gets ther distance in miles, checks if it needs to be converted into km or not
                String distance;
                if (metricKmMi.equals("km")){
                    DecimalFormat df = new DecimalFormat("#.#");
                    df.setRoundingMode(RoundingMode.CEILING);
                    String temp[] = route.get(i).getDistanceText().split("m");
                    double mile = Double.parseDouble(temp[0].trim());
                    distance = ""+(df.format(mile*1.609344)+metricKmMi);
                }
                else{
                    distance = route.get(i).getDistanceText();
                }

                //Displays the distance and time to the user
                distanceTime.setText("Distance "+distance+"\n"+"Duration "+route.get(i).getDurationText());
                //Toast.makeText(getApplicationContext(),"Route "+ (i+1) +": distance - "+ route.get(i).getDistanceText()+": duration - "+ route.get(i).getDurationText(),Toast.LENGTH_SHORT).show();

            }
            else {

            }

        }

        //Add Marker on route starting position
        MarkerOptions startMarker = new MarkerOptions();
        startMarker.position(polylineStartLatLng);
        startMarker.title("My Location");
        mMap.addMarker(startMarker);

        //Add Marker on route ending position
        MarkerOptions endMarker = new MarkerOptions();
        endMarker.position(polylineEndLatLng);
        endMarker.title("Destination");
        mMap.addMarker(endMarker);

        SaveRoutes();
    }

    @Override
    public void onRoutingCancelled() {
        Findroutes(userLatLng,destination,travelMode);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Findroutes(userLatLng,destination,travelMode);

    }

    //Will save the route which the user took, into the database.
    public void SaveRoutes()
    {
        //A check to make sure we get the first entry of an address
        if(latlngCheck == 0) {
            userTrips = destinationName+"+";
            latlngCheck++;
        }

        //Gets current user ID
        String userID = fAuth.getCurrentUser().getUid();
        /*
        Since I used firestore, I decided to store all the addresses into one field with delimiters
         */
        DocumentReference documentReference = fStore.collection("users").document(userID);
        documentReference.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot documentSnapshot) {
                Log.d(TAG, "WORKING");

                if (documentSnapshot.exists())
                {
                    String trips = documentSnapshot.getString("userTrips");
                    userTrips = trips+destinationName+"+";
                }
                else{

                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(MapsActivity.this, "Error!", Toast.LENGTH_SHORT).show();
                Log.d(TAG, e.toString());
            }
        });

        Map<String,Object> user = new HashMap<>();
        user.put("userTrips",userTrips);
        documentReference.update(user).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                Log.d(TAG, "onSuccess: Trip Saved");
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.d(TAG, "onFailure: " + e.toString());
            }
        });
    }

    //Checks the current metric settings by the user, gets this information from firestore
    public void metricCheck()
    {
        String userID = fAuth.getCurrentUser().getUid();
        DocumentReference documentReference = fStore.collection("users").document(userID);
        documentReference.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot documentSnapshot) {
                //Log.d(TAG, "WORKING");
                if (documentSnapshot.exists())
                {
                    metricKmMi = documentSnapshot.getString("metric");
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(MapsActivity.this, "Error!", Toast.LENGTH_SHORT).show();
                Log.d(TAG, e.toString());
            }
        });
    }

    //Allows the user to change their metric and stores it in firestore
    public void metricChange()
    {
        String change;

        if(metricKmMi.equals("km")) {
            change = "mi";
        }
        else{
            change = "km";
        }

        String userID = fAuth.getCurrentUser().getUid();
        DocumentReference documentReference = fStore.collection("users").document(userID);
        Map<String,Object> user = new HashMap<>();
        user.put("metric",change);
        documentReference.update(user).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                Log.d(TAG, "onSuccess: metric changed");
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.d(TAG, "onFailure: " + e.toString());
            }
        });
    }
}

