package study.querydsl;

import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.QTeam;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static study.querydsl.entity.QMember.*;
import static study.querydsl.entity.QTeam.*;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {

	@Autowired
	EntityManager em;

	JPAQueryFactory queryFactory;

	@BeforeEach // 개별 테스트 실행 전 수행
	public void before() {
		queryFactory = new JPAQueryFactory(em); // 멀티쓰레드 환경에서 동시성 문제없이 된다.
		Team teamA = new Team("teamA");
		Team teamB = new Team("teamB");
		em.persist(teamA);
		em.persist(teamB);

		Member member1 = new Member("member1", 10, teamA);
		Member member2 = new Member("member2", 20, teamA);

		Member member3 = new Member("member3", 30, teamB);
		Member member4 = new Member("member4", 40, teamB);

		em.persist(member1);
		em.persist(member2);
		em.persist(member3);
		em.persist(member4);
	}

	@Test
	public void startJPQL() {
		// member1 을 찾아라

		String qlString = "select m from Member m where m.username = :username";

		Member findMember = em.createQuery(qlString, Member.class)
				.setParameter("username", "member1")
				.getSingleResult();

		assertThat(findMember.getUsername()).isEqualTo("member1");
	}

	@Test
	public void startQuerydsl() {
//        JPAQueryFactory queryFactory = new JPAQueryFactory(em); // 필드레벨로 가져가도 좋다.

		// 방식 1
//        QMember m = new QMember("m");   // 어떤 QMember 인지 구분하는 문자를 준다.

		// 방식 2
//        QMember m2 = QMember.member;

		// 방식 3 (권장) : static import 활용
		Member findMember = queryFactory
				.select(member)
				.from(member)
				.where(member.username.eq("member1"))    // 자동으로 PrepareStatement의 파라미터 binding 방식 사용. 콘솔 창에서 ? 를 볼 수 있다.
				.fetchOne();

		assertThat(findMember.getUsername()).isEqualTo("member1");
	}

	@Test
	public void search() {
		Member findMember = queryFactory
				.selectFrom(member) // select(member).from(member) 와 동일
				.where(member.username.eq("member1")
						.and(member.age.eq(10)))
				.fetchOne();

		assertThat(findMember.getUsername()).isEqualTo("member1");
	}

	@Test
	public void searchAndParam() {
		Member findMember = queryFactory
				.selectFrom(member) // select(member).from(member) 와 동일
				.where(
						member.username.eq("member1"),  // , == .and
						member.age.eq(10)
				)
				.fetchOne();

		assertThat(findMember.getUsername()).isEqualTo("member1");
	}

	@Test
	public void resultFetch() {
		List<Member> fetch = queryFactory
				.selectFrom(member)
				.fetch();

		/* 값이 1개 이상일 때 Error 발생
		Member member = queryFactory
				.selectFrom(QMember.member)
				.fetchOne();*/

		Member fetchFirst = queryFactory
				.selectFrom(member)
				.fetchFirst(); // .limit(1).fetchOne()

		// fetchResults()는 deprecated 되었다.
		QueryResults<Member> results = queryFactory
				.selectFrom(member)
				.fetchResults();

		results.getTotal();
		List<Member> content = results.getResults();

		// fetchCount()는 deprecated 되었다.
		long total = queryFactory
				.selectFrom(member)
				.fetchCount();
	}

	/**
	 * 회원 정렬 순서
	 * 1. 회원 나이 내림차순(desc)
	 * 2. 회원 이름 오름차순(asc)
	 * 단, 2에서 회원 이름이 없으면 마지막에 출력(nulls last)
	 */
	@Test
	public void sort() {
		em.persist(new Member(null, 100));
		em.persist(new Member("member5", 100));
		em.persist(new Member("member6", 100));

		List<Member> result = queryFactory
				.selectFrom(member)
				.where(member.age.eq(100))
				.orderBy(member.age.desc(), member.username.asc().nullsLast())
				.fetch();

		Member member5 = result.get(0);
		Member member6 = result.get(1);
		Member memberNull = result.get(2);
		assertThat(member5.getUsername()).isEqualTo("member5");
		assertThat(member6.getUsername()).isEqualTo("member6");
		assertThat(memberNull.getUsername()).isNull();
	}

	@Test
	public void paging1() {
		List<Member> result = queryFactory
				.selectFrom(member)
				.orderBy(member.username.desc())
				.offset(1)
				.limit(2)
				.fetch();

		assertThat(result.size()).isEqualTo(2);
	}

	@Test
	public void aggregation() {
		List<Tuple> result = queryFactory
				.select(
						member.count(),
						member.age.sum(),
						member.age.avg(),
						member.age.max(),
						member.age.min()
				)
				.from(member)
				.fetch();

		Tuple tuple = result.get(0);
		assertThat(tuple.get(member.count())).isEqualTo(4);
		assertThat(tuple.get(member.age.sum())).isEqualTo(100);
		assertThat(tuple.get(member.age.avg())).isEqualTo(25);
		assertThat(tuple.get(member.age.max())).isEqualTo(40);
		assertThat(tuple.get(member.age.min())).isEqualTo(10);
	}

	/**
	 * TODO: 팀의 이름과 각 팀의 평균 연령을 구해라.
	 */
	@Test
	public void group() throws Exception {
		List<Tuple> result = queryFactory
				.select(team.name,
						member.age.avg()
				)
				.from(member)
				.join(member.team, team)
				.groupBy(team.name)
				.fetch();

		Tuple teamA = result.get(0);
		Tuple teamB = result.get(1);

		assertThat(teamA.get(team.name)).isEqualTo("teamA");
		assertThat(teamA.get(member.age.avg())).isEqualTo(15);

		assertThat(teamB.get(team.name)).isEqualTo("teamB");
		assertThat(teamB.get(member.age.avg())).isEqualTo(35);
	}

	/**
	 * 팀 A에 소속된 모든 회원
	 */
	@Test
	public void join() {
		List<Member> result = queryFactory
				.selectFrom(member)
				.join(member.team, team)
				.where(team.name.eq("teamA"))
				.fetch();

		assertThat(result)
				.extracting("username")
				.containsExactly("member1", "member2");
	}

	/**
	 * 세타 조인 (연관 관계 없는 조인. 일명 '막조인')
	 * 회원의 이름이 팀 이름과 같은 회원 조회
	 */
	@Test
	public void theta_join() {
		em.persist(new Member("teamA"));
		em.persist(new Member("teamB"));
		em.persist(new Member("teamC"));

		List<Member> result = queryFactory
				.select(member)
				.from(member, team)
				.where(member.username.eq(team.name))
				.fetch();

		assertThat(result)
				.extracting("username")
				.containsExactly("teamA", "teamB");
	}

	/**
	 * 예) 회원과 팀을 조인하면서, 팀 이름이 teamA인 팀만 조인, 회원은 모두 조회
	 * JPQL: select m, t from Member m left join m.team on t.name = 'teamA'
	 */
	@Test
	public void join_on_filtering() {
		List<Tuple> result = queryFactory
				.select(member, team)
				.from(member)
				.leftJoin(member.team, team)
				.on(team.name.eq("teamA"))
				.fetch();

		for (Tuple tuple : result) {
			System.out.println("tuple = " + tuple);
		}
	}

	/**
	 * 세타 조인 (연관 관계 없는 엔티티 외부 조인. 일명 '막조인')
	 * 회원의 이름이 팀 이름과 같은 대상 외부 조인
	 * JPQL: select m, t from Member m left join m.team on t.name = m.username
	 */
	@Test
	public void join_on_no_relation() {
		em.persist(new Member("teamA"));
		em.persist(new Member("teamB"));
		em.persist(new Member("teamC"));

		List<Tuple> result = queryFactory
				.select(member, team)
				.from(member)
				.leftJoin(team)	// 문법 주의!! leftJoin(member.team, team) 이렇게 쓰지 않는다. 왜냐하면 member가 가진 team_id와 team의 id가 갖다는 의미이므로
				.on(team.name.eq(member.username))
				.fetch();

		for (Tuple tuple : result) {
			System.out.println("tuple = " + tuple);
		}
	}

	@PersistenceUnit
	EntityManagerFactory emf;

	@Test
	public void fetchJoinNo() {
		em.flush();
		em.clear();

		Member findMember = queryFactory
				.selectFrom(member)
				.where(member.username.eq("member1"))
				.fetchOne();

		/**
		 * fetch join이 적용되지 않았으므로 로딩되면 안된다.
		 * 즉, Member에서 Team 엔티티가 FetchType.LAZY 전략을 취하므로 위의 쿼리에서 team은 나오지 않는다.
		 */
		boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
		assertThat(loaded).as("페치 조인 미적용").isFalse();
	}

	@Test
	public void fetchJoinUse() {
		em.flush();
		em.clear();

		Member findMember = queryFactory
				.selectFrom(member)
				.join(member.team, team).fetchJoin()	// 일반 join문 뒤에 .fetchJoin() 만 추가하면 된다.
				.where(member.username.eq("member1"))
				.fetchOne();

		/**
		 * fetch join이 적용했으므로 로딩된다.
		 */
		boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
		assertThat(loaded).as("페치 조인 적용").isTrue();
	}

	/**
	 * 나이가 가장 많은 회원 조회
	 */
	@Test
	public void whereSubQuery() {

		// alias가 중복되면 안되는 경우에는 QMember를 직접 생성한다. 여기선 member가 겹치지 않기 위해서.
		// SQL과 동일한 원리(ex. Member m1, Member m2 로 구분)
		QMember memberSub = new QMember("memberSub");

		List<Member> result = queryFactory
				.selectFrom(member)
				.where(member.age.eq(
						JPAExpressions	// static import 가능
								.select(memberSub.age.max())    // 서브 쿼리 (결과는 40)
								.from(memberSub)
				))
				.fetch();

		assertThat(result).extracting("age")
				.containsExactly(40);
	}

	/**
	 * 나이가 평균 이상인 회원
	 */
	@Test
	public void whereSubQueryGoe() {

		// alias가 중복되면 안되는 경우에는 QMember를 직접 생성한다. 여기선 member가 겹치지 않기 위해서.
		// SQL과 동일한 원리(ex. Member m1, Member m2 로 구분)
		QMember memberSub = new QMember("memberSub");

		List<Member> result = queryFactory
				.selectFrom(member)
				.where(member.age.goe(
						JPAExpressions
								.select(memberSub.age.avg())    // 서브 쿼리 (결과는 25)
								.from(memberSub)
				))
				.fetch();

		assertThat(result).extracting("age")
				.containsExactly(30, 40);
	}

	/**
	 * in 예제 (아래 예제는 효율적이지 않은 쿼리이긴 하다)
	 */
	@Test
	public void whereSubQueryIn() {

		// alias가 중복되면 안되는 경우에는 QMember를 직접 생성한다. 여기선 member가 겹치지 않기 위해서.
		// SQL과 동일한 원리(ex. Member m1, Member m2 로 구분)
		QMember memberSub = new QMember("memberSub");

		List<Member> result = queryFactory
				.selectFrom(member)
				.where(member.age.in(
						JPAExpressions
								.select(memberSub.age)
								.from(memberSub)
								.where(memberSub.age.gt(10))
				))
				.fetch();

		assertThat(result).extracting("age")
				.containsExactly(20, 30, 40);
	}

	@Test
	public void selectSubQuery() {

		QMember memberSub = new QMember("memberSub");

		List<Tuple> result = queryFactory
				.select(member.username,
						JPAExpressions
								.select(memberSub.age.avg())
								.from(memberSub))
				.from(member)
				.fetch();

		for (Tuple tuple : result) {
			System.out.println("tuple = " + tuple);
		}
	}

	@Test
	public void basicCase() {
		List<String> result = queryFactory
				.select(member.age
						.when(10).then("열살")
						.when(20).then("스무살")
						.otherwise("기타"))
				.from(member)
				.fetch();

		for (String s : result) {
			System.out.println("s = " + s);
		}
	}

	/**
	 * DB는 raw 데이터를 최소한으로 filtering, grouping 하는 역할만 하자.
	 * case문 같은 조건 처리는 애플리케이션단 혹은 presentation layer 에서 처리하는 걸 권장.
	 */
	@Test
	public void complexCase() {
		List<String> result = queryFactory
				.select(new CaseBuilder()
						.when(member.age.between(0, 20)).then("0~20살")
						.when(member.age.between(21, 30)).then("21~30살")
						.otherwise("기타"))
				.from(member)
				.fetch();

		for (String s : result) {
			System.out.println("s = " + s);
		}
	}

	@Test
	public void constant() {
		List<Tuple> result = queryFactory
				.select(member.username, Expressions.constant("A"))
				.from(member)
				.fetch();

		for (Tuple tuple : result) {
			System.out.println("tuple = " + tuple);
		}
	}

	@Test
	public void concat() {

		// {username}_{age}
		List<String> result = queryFactory
				.select(member.username.concat("_").concat(member.age.stringValue()))	// stringValue 유용하다.
				.from(member)
				.where(member.username.eq("member1"))
				.fetch();

		for (String s : result) {
			System.out.println("s = " + s);
		}
	}
}
