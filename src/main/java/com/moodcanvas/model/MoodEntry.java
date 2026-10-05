package com.moodcanvas.model;
import java.sql.Timestamp;
public class MoodEntry {private final int moodID,userID;private final String moodText,moodTag;private final Timestamp submittedAt;public MoodEntry(int i,int u,String t,String g,Timestamp s){moodID=i;userID=u;moodText=t;moodTag=g;submittedAt=s;}public int getMoodID(){return moodID;}public int getUserID(){return userID;}public String getMoodText(){return moodText;}public String getMoodTag(){return moodTag;}public Timestamp getSubmittedAt(){return submittedAt;}}
