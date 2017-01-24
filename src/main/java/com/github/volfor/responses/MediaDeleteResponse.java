package com.github.volfor.responses;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class MediaDeleteResponse extends Response {

    @SerializedName("did_delete")
    private boolean didDelete;

}
