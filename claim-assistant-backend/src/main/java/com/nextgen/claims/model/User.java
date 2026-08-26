package com.nextgen.claims.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "users")
public class User {

    @Id
    private String userId;      // login username, e.g. "john.smith"
    private String password;     // plain text — no security framework in scope
    private String name;
    private String email;
    private String customerId;  // links to Policy.customerId
}
