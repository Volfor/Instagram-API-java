package com.github.volfor.responses;

import com.github.volfor.models.Media;
import lombok.Data;

@Data
public class EditMediaResponse extends Response {

    private Media media;

}
