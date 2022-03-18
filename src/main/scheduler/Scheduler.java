package scheduler;

import scheduler.db.ConnectionManager;
import scheduler.model.Caregiver;
import scheduler.model.Patient;
import scheduler.model.Vaccine;
import scheduler.util.Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Random;

public class Scheduler {

    // objects to keep track of the currently logged-in user
    // Note: it is always true that at most one of currentCaregiver and currentPatient is not null
    //       since only one user can be logged-in at a time
    //To Do: pass in con as a parameter for helper methods, close cm in main
    private static Caregiver currentCaregiver = null;
    private static Patient currentPatient = null;

    public static void main(String[] args) {
        // printing greetings text
        System.out.println();
        System.out.println("Welcome to the COVID-19 Vaccine Reservation Scheduling Application!");
        System.out.println("*** Please enter one of the following commands ***");
        System.out.println("> create_patient <username> <password>");  //TODO: implement create_patient (Part 1)
        System.out.println("> create_caregiver <username> <password>");
        System.out.println("> login_patient <username> <password>");  // TODO: implement login_patient (Part 1)
        System.out.println("> login_caregiver <username> <password>");
        System.out.println("> search_caregiver_schedule <date>");  // TODO: implement search_caregiver_schedule (Part 2)
        System.out.println("> reserve <date> <vaccine>");  // TODO: implement reserve (Part 2)
        System.out.println("> upload_availability <date>");
        System.out.println("> cancel <appointment_id>");  // TODO: implement cancel (extra credit)
        System.out.println("> add_doses <vaccine> <number>");
        System.out.println("> show_appointments");  // TODO: implement show_appointments (Part 2)
        System.out.println("> logout");  // TODO: implement logout (Part 2)
        System.out.println("> quit");
        System.out.println();

        // read input from user
        BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print("> ");
            String response = "";
            try {
                response = r.readLine();
            } catch (IOException e) {
                System.out.println("Please try again!");
            }
            // split the user input by spaces
            String[] tokens = response.split(" ");
            // check if input exists
            if (tokens.length == 0) {
                System.out.println("Please try again!");
                continue;
            }
            // determine which operation to perform
            String operation = tokens[0];
            if (operation.equals("create_patient")) {
                createPatient(tokens);
            } else if (operation.equals("create_caregiver")) {
                createCaregiver(tokens);
            } else if (operation.equals("login_patient")) {
                loginPatient(tokens);
            } else if (operation.equals("login_caregiver")) {
                loginCaregiver(tokens);
            } else if (operation.equals("search_caregiver_schedule")) {
                searchCaregiverSchedule(tokens);
            } else if (operation.equals("reserve")) {
                reserve(tokens);
            } else if (operation.equals("upload_availability")) {
                uploadAvailability(tokens);
            } else if (operation.equals("cancel")) {
                cancel(tokens);
            } else if (operation.equals("add_doses")) {
                addDoses(tokens);
            } else if (operation.equals("show_appointments")) {
                showAppointments(tokens);
            } else if (operation.equals("logout")) {
                logout(tokens);
            } else if (operation.equals("quit")) {
                System.out.println("Bye!");
                return;
            } else {
                System.out.println("Invalid operation name!");
            }
        }
    }

    private static void createPatient(String[] tokens) {
        // create_patient <username> <password>
        // check 1: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Please try again!");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];
        // check 2: check if the username has been taken already
        if (usernameExists(username, "Patients")) {
            System.out.println("Username taken, try again!");
            return;
        }
        byte[] salt = Util.generateSalt();
        byte[] hash = Util.generateHash(password, salt);
        // create the patient
        try {
            currentPatient = new Patient.PatientBuilder(username, salt, hash).build();
            // save to patient information to our database
            currentPatient.saveToDB();
            System.out.println(" *** Account created successfully *** ");
        } catch (SQLException e) {
            System.out.println("Create failed");
            e.printStackTrace();
        }
    }

    private static void createCaregiver(String[] tokens) {
        // create_caregiver <username> <password>
        // check 1: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Please try again!");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];
        // check 2: check if the username has been taken already
        if (usernameExists(username, "Caregivers")) {
            System.out.println("Username taken, try again!");
            return;
        }
        byte[] salt = Util.generateSalt();
        byte[] hash = Util.generateHash(password, salt);
        // create the caregiver
        try {
            currentCaregiver = new Caregiver.CaregiverBuilder(username, salt, hash).build();
            // save to caregiver information to our database
            currentCaregiver.saveToDB();
            System.out.println(" *** Account created successfully *** ");
        } catch (SQLException e) {
            System.out.println("Create failed");
            e.printStackTrace();
        }
    }

    private static boolean usernameExists(String username, String userType) {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();
        String selectUsername = "SELECT * FROM " + userType + " WHERE Username = ?";
        try {
            PreparedStatement statement = con.prepareStatement(selectUsername);
            statement.setString(1, username);
            ResultSet resultSet = statement.executeQuery();
            // returns false if the cursor is not before the first record or if there are no rows in the ResultSet.
            return resultSet.isBeforeFirst();
        } catch (SQLException e) {
            System.out.println("Error occurred when checking username");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
        return true;
    }

    private static void loginPatient(String[] tokens) {
        // login_patient <username> <password>
        // check 1: if someone's already logged-in, they need to log out first
        if (currentCaregiver != null || currentPatient != null) {
            System.out.println("Already logged-in!");
            return;
        }
        // check 2: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Please try again!");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];

        Patient patient = null;
        try {
            patient = new Patient.PatientGetter(username, password).get();
        } catch (SQLException e) {
            System.out.println("Error occurred when logging in");
            e.printStackTrace();
        }
        // check if the login was successful
        if (patient == null) {
            System.out.println("Please try again!");
        } else {
            System.out.println("Patient logged in as: " + username);
            currentPatient = patient;
        }
    }

    private static void loginCaregiver(String[] tokens) {
        // login_caregiver <username> <password>
        // check 1: if someone's already logged-in, they need to log out first
        if (currentCaregiver != null || currentPatient != null) {
            System.out.println("Already logged-in!");
            return;
        }
        // check 2: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Please try again!");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];

        Caregiver caregiver = null;
        try {
            caregiver = new Caregiver.CaregiverGetter(username, password).get();
        } catch (SQLException e) {
            System.out.println("Error occurred when logging in");
            e.printStackTrace();
        }
        // check if the login was successful
        if (caregiver == null) {
            System.out.println("Please try again!");
        } else {
            System.out.println("Caregiver logged in as: " + username);
            currentCaregiver = caregiver;
        }
    }

    private static void searchCaregiverSchedule(String[] tokens) {
        // TODO: Part 2
        // search_caregiver_schedule <date>
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("Please login first!");
            return;
        }
        try {
            ArrayList<String> caregivers = getAvailability(Date.valueOf(tokens[1]));
            if (caregivers.size() < 1) {
                System.out.println("No available caregivers for this date.");
            } else {
                for (String avail : caregivers) {
                    System.out.println(avail);
                }
            }
            for (String dose : getVaccines()) {
                System.out.println(dose);
            }
        } catch (IllegalArgumentException e) {
            System.out.println("Please enter a valid date!");
        }
    }

    private static ArrayList<String> getAvailability(Date time) {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();
        ArrayList<String> caregivers = new ArrayList<>();
        String getSchedule = "SELECT * FROM Availabilities WHERE Time = ?";
        try {
            PreparedStatement statement = con.prepareStatement(getSchedule);
            statement.setDate(1, time);
            ResultSet results = statement.executeQuery();
            while (results.next()) {
                caregivers.add(results.getString("Username"));
            }
        } catch (SQLException e) {
            System.out.println("System Error. Please try another time");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
        return caregivers;
    }

    private static ArrayList<String> getVaccines() {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();
        ArrayList<String> availDoses = new ArrayList<>();
        try {
            PreparedStatement statement = con.prepareStatement("SELECT * FROM Vaccines");
            ResultSet results = statement.executeQuery();
            while (results.next()) {
                availDoses.add(results.getString("Name") + ": " +
                               results.getString("Doses") + " doses available.");
            }
            return availDoses;
        } catch (SQLException e) {
            System.out.println("System Error. Please try another time");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
        return null;
    }

    private static int getVaccines(String name) {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();
        ArrayList<String> availDoses = new ArrayList<>();
        String getVaccine = "SELECT * FROM Vaccines WHERE name = ?";
        try {
            PreparedStatement statement = con.prepareStatement(getVaccine);
            statement.setString(1, name);
            ResultSet results = statement.executeQuery();
            results.next();
            return results.getInt("Doses");
        } catch (SQLException e) {
            System.out.println("System Error. Please try another time");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
        return -1;
    }

    private static void reserve(String[] tokens) {
        //reserve <date> <vaccine>
        //patient login check
        if (currentPatient == null) {
            System.out.println("Please login as a patient first!");
            return;
        }
        String vaxName = tokens[2];
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();
        try {
            Date date = Date.valueOf(tokens[1]);
            ArrayList<String> caregivers = getAvailability(date);
            if (getVaccines(vaxName) < 1) {
                System.out.println("No more available " + vaxName + " vaccines. Try again with a different vaccine.");
                return;
            } if (caregivers.size() < 1) {
                System.out.println("No available caregivers for that date. Please try another date.");
                return;
            }
            Random rand = new Random();
            String doubleBooked = "SELECT COUNT(*) FROM Appointments WHERE Appointments.Patient = ? " +
                    "                                                AND Appointments.Time = ?";
            String makeReservation = "INSERT INTO Appointments VALUES (? , ? , ? , ? , ?)";
            String deleteReservation = "DELETE FROM Availabilities WHERE Time = ? AND Username = ?";
            //checks to see if the user is double-booked
            PreparedStatement statement = con.prepareStatement(doubleBooked);
            statement.setString(1, currentPatient.getUsername());
            statement.setDate(2, date);
            ResultSet results = statement.executeQuery();
            results.next();
            if (results.getInt(1) > 0) {
                System.out.println("You already have a reservation booked on this date.");
                return;
            }
            //makes reservation
            statement = con.prepareStatement(makeReservation);
            String appointment = "Appointment Created! Your Appointment ID is ";
            int newId = makeNewAID();
            appointment += newId;
            statement.setInt(1, newId);
            String caregiver = caregivers.get(rand.nextInt(caregivers.size()));
            appointment += " with " + caregiver + ".";
            statement.setString(2, caregiver);
            statement.setString(3, currentPatient.getUsername());
            statement.setString(4, vaxName);
            statement.setDate(5, date);
            statement.executeUpdate();
            System.out.println(appointment);
            //updates availability
            statement = con.prepareStatement(deleteReservation);
            statement.setDate(1, date);
            statement.setString(2, caregiver);
            statement.executeUpdate();
            //deincrements vaccine count
            Vaccine vax = new Vaccine.VaccineGetter(vaxName).get();
            vax.decreaseAvailableDoses(1);
        } catch (SQLException e) {
            System.out.println("System Error. Please try another time");
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            System.out.println("Please enter a valid date!");
        } finally {
            cm.closeConnection();
        }
    }

    private static int makeNewAID(){
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();
        try {
            PreparedStatement statement = con.prepareStatement("SELECT MAX(AID) FROM Appointments");
            ResultSet results = statement.executeQuery();
            results.next();
            return results.getInt(1) + 1;
        } catch (SQLException e) {
            System.out.println("System Error. Please try another time");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
        return -1;
    }

    private static void uploadAvailability(String[] tokens) {
        // upload_availability <date>
        // check 1: check if the current logged-in user is a caregiver
        if (currentCaregiver == null) {
            System.out.println("Please login as a caregiver first!");
            return;
        }
        // check 2: the length for tokens need to be exactly 2 to include all information (with the operation name)
        if (tokens.length != 2) {
            System.out.println("Please try again!");
            return;
        }
        String date = tokens[1];
        try {
            Date d = Date.valueOf(date);
            currentCaregiver.uploadAvailability(d);
            System.out.println("Availability uploaded!");
        } catch (IllegalArgumentException e) {
            System.out.println("Please enter a valid date!");
        } catch (SQLException e) {
            System.out.println("Error occurred when uploading availability");
            e.printStackTrace();
        }
    }

    private static void cancel(String[] tokens) {
        // TODO: Extra credit
        //cancel <appointment_id>
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("You must login first!");
            return;
        }
        int AID = Integer.parseInt(tokens[1]);
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();
        String reservationInfo = "SELECT * FROM Appointments WHERE AID = ?";
        String deleteReservation = "DELETE FROM Appointments WHERE AID = ?";
        try {
            //stores appointment info
            PreparedStatement statement = con.prepareStatement(reservationInfo);
            statement.setInt(1, AID);
            ResultSet results = statement.executeQuery();
            results.next();
            Date date = results.getDate("Time");
            String caregiver = results.getString("Caregiver");
            String vaxName = results.getString("Vaccine");
            //removes appointment
            statement = con.prepareStatement(deleteReservation);
            statement.setInt(1, AID);
            statement.executeUpdate();
            //re-updates availability
            updateAvailability(date, caregiver);
            System.out.println("Appointment " + AID + " was successfully canceled");
            //increments available vaccine doses
            Vaccine vax = new Vaccine.VaccineGetter(vaxName).get();
            vax.increaseAvailableDoses(1);
        } catch (SQLException e) {
            System.out.println("System Error. Please try another time");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
    }

    //called from cancel method
    private static void updateAvailability(Date d, String caregiver) {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();
        String addAvailability = "INSERT INTO Availabilities VALUES (? , ?)";
        try {
            PreparedStatement statement = con.prepareStatement(addAvailability);
            statement.setDate(1, d);
            statement.setString(2, caregiver);
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("System Error. Please try another time");
            e.printStackTrace();
        }
    }

    private static void addDoses(String[] tokens) {
        // add_doses <vaccine> <number>
        // check 1: check if the current logged-in user is a caregiver
        if (currentCaregiver == null) {
            System.out.println("Please login as a caregiver first!");
            return;
        }
        // check 2: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Please try again!");
            return;
        }
        String vaccineName = tokens[1];
        int doses = Integer.parseInt(tokens[2]);
        Vaccine vaccine = null;
        try {
            vaccine = new Vaccine.VaccineGetter(vaccineName).get();
        } catch (SQLException e) {
            System.out.println("Error occurred when adding doses");
            e.printStackTrace();
        }
        // check 3: if getter returns null, it means that we need to create the vaccine and insert it into the Vaccines
        //          table
        if (vaccine == null) {
            try {
                vaccine = new Vaccine.VaccineBuilder(vaccineName, doses).build();
                vaccine.saveToDB();
            } catch (SQLException e) {
                System.out.println("Error occurred when adding doses");
                e.printStackTrace();
            }
        } else {
            // if the vaccine is not null, meaning that the vaccine already exists in our table
            try {
                vaccine.increaseAvailableDoses(doses);
            } catch (SQLException e) {
                System.out.println("Error occurred when adding doses");
                e.printStackTrace();
            }
        }
        System.out.println("Doses updated!");
    }

    private static void showAppointments(String[] tokens) {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("You must login first!");
            return;
        }
        String name;
        String type;
        String altName;
        String altType;
        if (currentCaregiver != null) {
            name = currentCaregiver.getUsername();
            type = "Caregiver";
            altName = "    Patient name: ";
            altType = "Patient";
        } else {
            name = currentPatient.getUsername();
            type = "Patient";
            altName = "    Caregiver name: ";
            altType = "Caregiver";
        }
        String findAppointments = "SELECT * FROM Appointments WHERE Appointments." + type + " = ?";
        try {
            PreparedStatement statement = con.prepareStatement(findAppointments);
            statement.setString(1, name);
            System.out.println("Scheduled appointments for " + name + ":");
            ResultSet results = statement.executeQuery();
            while (results.next()) {
                String appointmentInfo;
                System.out.println("Appointment ID: " + results.getInt("AID"));
                System.out.println("    Vaccine Name: " + results.getString("Vaccine"));
                System.out.println("    Date: " + results.getDate("Time"));
                System.out.println(altName + results.getString(altType));
            }
        } catch (SQLException e) {
            System.out.println("System Error. Please try another time");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
    }

    private static void logout(String[] tokens) {
        if (currentPatient != null || currentCaregiver != null) {
            currentPatient = null;
            currentCaregiver = null;
            System.out.println("Logout successful.");
        } else {
            System.out.println("Please log in first.");
        }
    }
}
