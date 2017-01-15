package com.github.volfor.responses;

import com.github.volfor.models.Media;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class UploadPhotoResponse extends Response {

    private Media media;
    @SerializedName("upload_id")
    private String uploadId;

}
