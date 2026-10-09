package com.example.app;

/** A Java class taken as a request body: the plugin leaves it to Spring and warns in the build. */
public class JavaTicketRequest {
    private String title;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public String toString() {
        return "JavaTicketRequest(title=" + title + ")";
    }
}
