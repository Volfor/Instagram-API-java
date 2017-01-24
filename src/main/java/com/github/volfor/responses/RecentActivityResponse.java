package com.github.volfor.responses;

import com.github.volfor.models.Counts;
import com.github.volfor.models.Story;
import lombok.Data;

import java.util.List;

@Data
public class RecentActivityResponse extends Response {

    private List<Story> newStories;
    private List<Story> old_stories;
    private boolean continuation; // ??
    private List<Story> friendRequestStories; // ??
    private Counts counts;
    private String subscription; // ??
    private long continuationToken; // ??

}
