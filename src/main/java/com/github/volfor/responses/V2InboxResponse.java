package com.github.volfor.responses;

import com.github.volfor.models.Inbox;
import com.github.volfor.models.User;
import lombok.Data;

import java.util.List;

@Data
public class V2InboxResponse extends Response {

    private int pendingRequestsTotal;
    private int seqId;
    private List<User> pendingRequestsUsers;
    private Inbox inbox;
    private String subscription; // ??

}
