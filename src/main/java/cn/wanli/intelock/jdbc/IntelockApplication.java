package cn.wanli.intelock.jdbc;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.jdbc.lock.DefaultLockRepository;
import org.springframework.integration.jdbc.lock.JdbcLockRegistry;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import javax.persistence.*;
import javax.sql.DataSource;
import java.io.Serializable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

@SpringBootApplication
public class IntelockApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntelockApplication.class, args);
    }

    @Bean
    public DefaultLockRepository registry(DataSource dataSource) {
        return new DefaultLockRepository(dataSource);
    }

    @Bean
    public JdbcLockRegistry jdbcLockRegistry(DefaultLockRepository repository) {
        return new JdbcLockRegistry(repository);
    }
}

@Slf4j
@RestController
@AllArgsConstructor
class LockInteController {

    private final LockRegistry registry;
    private final Userdao userdao;

    @SneakyThrows
    @GetMapping("/user/{id}/{name}/{time}")
    public User updateUser(@PathVariable Long id,
                           @PathVariable String name,
                           @PathVariable Long time) {
        String key = Long.toString(id);
        Lock lock = registry.obtain(key);
        boolean lockAcquired = lock.tryLock(1, TimeUnit.SECONDS);
        if (lockAcquired) {
            try {
                doUpdateUser(id, name);
                Thread.sleep(time);
            } finally {
                lock.unlock();
            }
        }
        System.out.println("查询");
        return userdao.findById(id);
    }

    User doUpdateUser(Long id, String name) {
        User u = userdao.findById(id);
        u.setName(name);
        return userdao.update(u);
    }
}

@Repository
@AllArgsConstructor
class Userdao {
    private final JdbcTemplate template;
    private final RowMapper<User> rowMapper = (rs, rowNum) -> {
        User user = new User();
        user.setName(rs.getString("name"));
        user.setId(rs.getLong("id"));
        return user;
    };

    User findById(Long id) {
        return template.queryForObject("select * from tb_user where id = ?", rowMapper, id);
    }

    User update(User user) {
        return template.execute("update tb_user set name = ? where id = ?", (PreparedStatementCallback<User>) ps -> {
            ps.setString(1, user.getName());
            ps.setLong(2, user.getId());
            ps.execute();
            return findById(user.getId());
        });
    }


}

@Entity
@Table(name = "tb_user")
@Data
@AllArgsConstructor
@NoArgsConstructor
class User implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
}