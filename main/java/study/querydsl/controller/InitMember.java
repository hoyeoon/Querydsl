package study.querydsl.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.entity.Member;
import study.querydsl.entity.Team;

import javax.annotation.PostConstruct;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

@Profile("local")
@Component
@RequiredArgsConstructor
public class InitMember {

    private final InitMemberSerivce initMemberSerivce;

    /**
     * local에서 Tomcat을 띄울 때 init을 실행하여 데이터를 넣고 시작.
     * Q. init() 안에 바로 for문 돌리면 되지 않나 ? -> 라이프 사이클이 달라서 이런 식으로 한다.
     */
    @PostConstruct
    public void init() {
        initMemberSerivce.init();
    }

    @Component
    static class InitMemberSerivce {
        @PersistenceContext
        private EntityManager em;

        @Transactional
        public void init() {
            Team teamA = new Team("teamA");
            Team teamB = new Team("teamB");
            em.persist(teamA);
            em.persist(teamB);

            for(int i = 0; i < 100; i++) {
                Team selectedTeam = i % 2 == 0 ? teamA : teamB;
                em.persist(new Member("member" + i, i, selectedTeam));
            }
        }
    }
}
