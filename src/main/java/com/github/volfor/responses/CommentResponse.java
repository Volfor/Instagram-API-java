package com.github.volfor.responses;

import com.github.volfor.models.Comment;
import lombok.Data;

@Data
public class CommentResponse extends Response {

    private Comment comment;

}
