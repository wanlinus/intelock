package cn.wanli.intelock.jpa;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.integration.jdbc.lock.DefaultLockRepository;
import org.springframework.integration.jdbc.lock.JdbcLockRegistry;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import javax.persistence.*;
import javax.sql.DataSource;
import java.io.Serializable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

@SpringBootApplication
public class JpalockApplication {

    public static void main(String[] args) {
        SpringApplication.run(JpalockApplication.class, args);
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
    private final UserRepository repository;

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
        return repository.findById(id).orElse(new User());
    }

    User doUpdateUser(Long id, String name) {
        return repository.findById(id).map(user -> {
            user.setName(name);
            return repository.save(user);
        }).orElse(new User());
    }
}

interface UserRepository extends JpaRepository<User, Long> {
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