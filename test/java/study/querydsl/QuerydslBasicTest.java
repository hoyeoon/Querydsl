package study.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
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
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;
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
@Transactional	// 기본적으로 rollback 한다. 다음 테스트에서 정상적으로 동작하도록
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

	@Test
	public void simpleProjection() {
		List<String> result = queryFactory
				.select(member.username)
				.from(member)
				.fetch();

		for (String s : result) {
			System.out.println("s = " + s);
		}
	}

	@Test
	public void tupleProjection() {
		/**
		 * com.querydsl.core 패키지 내에 존재하는 Tuple은 repository 계층 내에서 쓰도록 하자.
		 * 바깥으로 던질 때는 DTO로 반환해야 한다!! Tuple도 querydsl에 종속적인 타입이기 때문
		 * 좋은 설계는 하부 기술을 바꾸더라도(ex. querydsl을 다른 걸로 변경) Controller, Service 가 바뀌지 않아야 한다.
		 */
		List<Tuple> result = queryFactory
				.select(member.username, member.age)
				.from(member)
				.fetch();

		for (Tuple tuple : result) {
			String username = tuple.get(member.username);
			int age = tuple.get(member.age);
			System.out.println("username = " + username);
			System.out.println("age = " + age);
		}
	}

	@Test
	public void findDtoByJPQL() {
		/**
		 * 순수 JPA에서 DTO를 조회할 때는 new 명령어를 사용해야 함
		 * DTO의 패키지 이름을 다 적어줘야 해서 지저분함
		 * 생성자 방식만 지원함(Setter, 필드에 값을 바로 주입이 불가능)
		 * */
		List<MemberDto> result = em.createQuery("select new study.querydsl.dto.MemberDto(m.username, m.age)" +
				" from Member m", MemberDto.class)
				.getResultList();

		for (MemberDto memberDto : result) {
			System.out.println("memberDto = " + memberDto);
		}
	}

	// Setter를 통해 값이 들어가는 방식
	@Test
	public void findBySetter() {
		List<MemberDto> result = queryFactory
				.select(Projections.bean(MemberDto.class,
						member.username,
						member.age))
				.from(member)
				.fetch();

		for (MemberDto memberDto : result) {
			System.out.println("memberDto = " + memberDto);
		}
	}

	// Getter, Setter 무시하고 값이 필드에 꽂힌다.
	@Test
	public void findByField() {
		List<MemberDto> result = queryFactory
				.select(Projections.fields(MemberDto.class,
						member.username,
						member.age))
				.from(member)
				.fetch();

		for (MemberDto memberDto : result) {
			System.out.println("memberDto = " + memberDto);
		}
	}

	// 생성자 방식 - 생성자는 타입으로 들어가기 때문에 MemberDto -> UserDto로 바뀌어도 이상없다.
	@Test
	public void findByConstructor() {
		List<UserDto> result = queryFactory
				.select(Projections.constructor(UserDto.class,
						member.username,
						member.age))
				.from(member)
				.fetch();

		for (UserDto userDto : result) {
			System.out.println("userDto = " + userDto);
		}
	}

	// Getter, Setter 무시하고 값이 필드에 꽂힌다.
	@Test
	public void findUserDto() {
		QMember memberSub = new QMember("memberSub");

		List<UserDto> result = queryFactory
				.select(Projections.fields(UserDto.class,
						member.username.as("name"),	// as 없이 member.username 의 경우 field 명이 달라 null 값이 채워진다.
						member.age,
						// as 하는 또 다른 방법
						ExpressionUtils.as(JPAExpressions
							.select(memberSub.age.max())
							.from(memberSub), "age")
				))
				.from(member)
				.fetch();

		for (UserDto userDto : result) {
			System.out.println("userDto = " + userDto);
		}
	}

	// MemberDto에 @QueryProjection 붙인 후 compileQuerydsl 실행하여 QMemberDto 만들고 진행
	/**
	 * 단점 - 실무에서 잘 사용하지 않는 이유
	 *
	 * 1. compileQuerydsl 과정이 귀찮다
	 * 2. Dto에 @QueryProjection를 붙이므로 querydsl에 대한 의존성이 생긴다.
	 *    - 만약 querydsl를 다른 기술로 변경한다면 함께 변경해야한다.
	 *    - DTO는 Service, Repository, Controller 전체에서 쓰일 수 있다.
	 *      여러 Layer에 걸쳐 돌아다닌다. DTO가 querydsl에 의존적으로 설계되어 의존적이다. 아키텍쳐적으로 아쉽다.
	 */
	@Test
	public void findDtoByQueryProjection() {
		List<MemberDto> result = queryFactory
				/**
				 * 생성자 방식(런타임 시점에 오류 잡음)과 다르게 컴파일 오류를 잡아준다는 장점이 있다.
				 */
				.select(new QMemberDto(member.username, member.age))
				.from(member)
				.fetch();

		for (MemberDto memberDto : result) {
			System.out.println("memberDto = " + memberDto);
		}
	}

	@Test
	public void dynamicQuery_BooleanBuilder() {
		String usernameParam = "member1";
		Integer ageParam = null;

		List<Member> result = searchMember1(usernameParam, ageParam);
		assertThat(result.size()).isEqualTo(1);
	}

	private List<Member> searchMember1(String usernameCond, Integer ageCond) {

		BooleanBuilder builder = new BooleanBuilder();
		if(usernameCond != null) {
			builder.and(member.username.eq(usernameCond));
		}

		if(ageCond != null) {
			builder.and(member.age.eq(ageCond));
		}

		return queryFactory
				.selectFrom(member)
				.where(builder)
				.fetch();
	}

	// 실무에서 많이 사용
	/**
	 * `where` 조건에 `null` 값은 무시된다.
	 * 메서드를 다른 쿼리에서도 재활용 할 수 있다.
	 * 쿼리 자체의 가독성이 높아진다.
	 */
	@Test
	public void dynamicQuery_WhereParam() {
		String usernameParam = "member1";
		Integer ageParam = null;

		List<Member> result = searchMember2(usernameParam, ageParam);
		assertThat(result.size()).isEqualTo(1);
	}

	private List<Member> searchMember2(String usernameCond, Integer ageCond) {
		return queryFactory
				.selectFrom(member)
//				.where(usernameEq(usernameCond), ageEq(ageCond))	// where 내에 null이 들어가면 무시됨
				.where(allEq(usernameCond, ageCond))
				.fetch();
	}

	private BooleanExpression ageEq(Integer ageCond) {
		return ageCond != null ? member.age.eq(ageCond) : null;
	}

	private BooleanExpression usernameEq(String usernameCond) {
		return usernameCond != null ? member.username.eq(usernameCond) : null;
	}

	// 조합이 가능하다 (`null` 체크는 주의해서 처리해야 함)
	private BooleanExpression allEq(String usernameCond, Integer ageCond) {
		return usernameEq(usernameCond).and(ageEq(ageCond));
	}

	/** 벌크 연산
	 *
	 * 벌크 연산은 영속성 컨텍스트를 무시하고, DB에 바로 쿼리를 넣는다.
	 * 따라서 두 상태가 다르게 되는 문제가 생긴다.
	 */
	@Test
//	@Commit	// @Transactional 기본설정이 rollback 이여서 잠시 DB 확인을 위해 @Commit 사용
	public void bulkUpdate() {

		// 실행 전
		// member1 = 10 -> DB member1
		// member2 = 20 -> DB member2
		// member3 = 30 -> DB member3
		// member4 = 40 -> DB member4

		long count = queryFactory // count에는 영향을 받은 row 수가 나간다.
				.update(member)
				.set(member.username, "비회원")
				.where(member.age.lt(28))
				.execute();

		// 실행 후
		// member1 = 10 -> DB 비회원
		// member2 = 20 -> DB 비회원
		// member3 = 30 -> DB member3
		// member4 = 40 -> DB member4

		// 벌크연산 -> em.flush(); em.clear(); 필수
		em.flush();
		em.clear();

		List<Member> result = queryFactory
				.selectFrom(member)
				.fetch();

		// em.flush(); em.clear(); 하지 않으면 DB와 다른 영속성 컨텍스트의 값을 가져온다.(repeatable read)
		// 따라서, 벌크 연산 이후 영속성 컨텍스트와 DB의 동기화를 위해서는
		// 항상 em.flush(); em.clear();를 날려주자.
		for (Member member1 : result) {
			System.out.println("member1 = " + member1);
		}
	}

	@Test
	public void bulkAdd() {
		long count = queryFactory
				.update(member)
				.set(member.age, member.age.add(1))
				.execute();
	}

	@Test
	public void bulkMuliply() {
		long count = queryFactory
				.update(member)
				.set(member.age, member.age.multiply(2))
				.execute();
	}

	@Test
	public void bulkDelete() {
		long count = queryFactory
				.delete(member)
				.where(member.age.gt(18))
				.execute();
	}

	@Test
	public void sqlFunction() {
		List<String> result = queryFactory
				.select(Expressions.stringTemplate("function('replace', {0}, {1}, {2})",
						member.username, "member", "M"))
				.from(member)
				.fetch();

		for (String s : result) {
			System.out.println("s = " + s);
		}
	}

	@Test
	public void sqlFunction2() {
		List<String> result = queryFactory
				.select(member.username)
				.from(member)
//				.where(member.username.eq(
//						Expressions.stringTemplate("function('lower', {0})", member.username)))
				.where(member.username.eq(member.username.lower()))
				.fetch();

		for (String s : result) {
			System.out.println("s = " + s);
		}
	}
}
