package study.querydsl.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;
import study.querydsl.dto.MemberSearchCondition;
import study.querydsl.dto.MemberTeamDto;
import study.querydsl.dto.QMemberTeamDto;
import study.querydsl.entity.QTeam;

import javax.persistence.EntityManager;
import java.util.List;

import static study.querydsl.entity.QMember.member;

/**
 * Custom 사용을 위해 무조건 interface를 활용한 MemberRepository 방식이 좋은 설계방식 이라고는 볼 수 없다.
 * 핵심비즈니스 로직으로써 재사용성이 있는 기능의 경우에는 좋을 것이다. (ex. 엔티티 검색 등)
 *
 * 하지만, 공용성이 없고 특정 API에 매우 종속되거나 화면에 특화된 기능의 경우 수정 Life cycle이 API나 화면에 맞춰 변경되므로
 * 그냥 MemberQueryRepository와 같이 Repository 클래스를 만들면 찾기도 편하고 괜찮은 방법이다.
 *
 * 기본은 Custom 사용. 프로젝트가 커지고 위의 경우에는 조회용 QueryRepository 도입을 고려해보자
 */
@Repository
public class MemberQueryRepository {
    private final JPAQueryFactory queryFactory;

    public MemberQueryRepository(EntityManager em) {
        this.queryFactory = new JPAQueryFactory(em);
    }

    public List<MemberTeamDto> search(MemberSearchCondition condition) {
        return queryFactory
                .select(new QMemberTeamDto(
                        member.id.as("memberId"),
                        member.username,
                        member.age,
                        QTeam.team.id.as("teamId"),
                        QTeam.team.name.as("teamName")
                ))
                .from(member)
                .leftJoin(member.team, QTeam.team)
                .where(
                        usernameEq(condition.getUsername()),
                        teamNameEq(condition.getTeamName()),
                        ageGoe(condition.getAgeGoe()),
                        ageLoe(condition.getAgeLoe())
                )
                .fetch();
    }

    // BooleanExpression으로 해야 나중에 composition할 때 많아서 좋다.
    private BooleanExpression usernameEq(String username) {
        return StringUtils.hasText(username) ? member.username.eq(username) : null;
    }

    private BooleanExpression teamNameEq(String teamName) {
        return StringUtils.hasText(teamName) ? QTeam.team.name.eq(teamName) : null;
    }

    private BooleanExpression ageGoe(Integer ageGoe) {
        return ageGoe != null ? member.age.goe(ageGoe) : null;
    }

    private BooleanExpression ageLoe(Integer ageLoe) {
        return ageLoe != null ? member.age.loe(ageLoe) : null;
    }

    // 이런 식으로 조립해서 쓸 수 있다는 장점이 있다. (null 체크는 더 해야 한다.)
    private BooleanExpression ageBetween(int ageLoe, int ageGoe) {
        return ageGoe(ageLoe).and(ageGoe(ageGoe));
    }
}
