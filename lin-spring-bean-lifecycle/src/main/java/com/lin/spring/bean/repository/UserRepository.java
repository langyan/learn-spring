package com.lin.spring.bean.repository;


import com.lin.spring.bean.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {


}
