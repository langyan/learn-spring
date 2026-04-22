package com.lin.spring.sharding;

import static org.assertj.core.api.Assertions.assertThat;

import com.lin.spring.sharding.dto.CreateUserRequest;
import com.lin.spring.sharding.dto.UserResponse;
import com.lin.spring.sharding.id.SnowflakeIdCodec;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 端到端：写路由与读路由命中预期物理库。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AppUserShardingIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private Map<Integer, DataSource> shardDataSources;

    @Test
    void createThenRead_routesToSameShardPhysicalDb() throws Exception {
        CreateUserRequest body = new CreateUserRequest();
        body.setEmail("route.integration@test.com");

        ResponseEntity<UserResponse> created =
                restTemplate.postForEntity("/users", body, UserResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UserResponse user = created.getBody();
        assertThat(user).isNotNull();
        assertThat(user.getShardId()).isEqualTo(SnowflakeIdCodec.extractShardId(user.getId()));

        ResponseEntity<UserResponse> got =
                restTemplate.getForEntity("/users/" + user.getId(), UserResponse.class);
        assertThat(got.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(got.getBody()).isNotNull();
        assertThat(got.getBody().getEmail()).isEqualTo("route.integration@test.com");

        int rowsOnShard = countUsersOnShard(user.getShardId());
        assertThat(rowsOnShard).isGreaterThanOrEqualTo(1);
        for (int s = 0; s < 3; s++) {
            if (s != user.getShardId()) {
                assertThat(countUsersOnShard(s)).isEqualTo(0);
            }
        }
    }

    private int countUsersOnShard(int shard) throws Exception {
        DataSource ds = shardDataSources.get(shard);
        try (Connection c = ds.getConnection();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM app_user")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
