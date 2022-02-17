package study.querydsl.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import study.querydsl.entity.Member;

import java.util.*;

public interface MemberRepository extends JpaRepository<Member, Long>, MemberRepositoryCustom {
    // 선언 만으로 select m from Member m where m.username = :username 으로 동작
    List<Member> findByUsername(String username);
}
