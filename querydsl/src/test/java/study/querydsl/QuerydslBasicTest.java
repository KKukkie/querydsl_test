package study.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.Team;
import study.querydsl.entity.dto.MemberDto;
import study.querydsl.entity.dto.QMemberDto;
import study.querydsl.entity.dto.UserDto;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;
import java.util.List;
import java.util.Objects;

import static com.querydsl.jpa.JPAExpressions.select;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.util.StringUtils.hasText;
import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.team;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {

    @Autowired
    private EntityManager entityManager;
    JPAQueryFactory queryFactory;

    @BeforeEach
    public void beforeTest() {
         queryFactory = new JPAQueryFactory(entityManager);

        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        entityManager.persist(teamA);
        entityManager.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);
        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);
        entityManager.persist(member1);
        entityManager.persist(member2);
        entityManager.persist(member3);
        entityManager.persist(member4);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    public void startJPQL() throws Exception {
        // 1) Given
        String queryStr = "select m from Member m where m.username = :username";
        Member member = entityManager.createQuery(
                        queryStr,
                        Member.class
                )
                .setParameter("username", "member1")
                .getSingleResult();

        // 2) When
        // 3) Then
        assertThat(member.getUsername()).isEqualTo("member1");
    }

    @Test
    public void startQueryDSL() throws Exception {
        // 1) Given
        //QMember m = new QMember("m");
        QMember m = member;

        // 2) When
        Member member = queryFactory
                .select(m)
                .from(m)
                .where(m.username.eq("member1")) // 파라미터 자동 바인딩됨
                .fetchOne();

        // 3) Then
        assertThat(member.getUsername()).isEqualTo("member1");
    }

    @Test
    public void search() throws Exception {
        // 1) Given
        Member member1 = queryFactory
                .selectFrom(member)
                .where(
                        member.username.eq("member1"),
                        (member.age.eq(10))
                        /*member.username.eq("member1")
                        .and(member.age.eq(10))*/
                )
                .fetchOne();

        // 2) When

        // 3) Then
        assertThat(member1.getUsername()).isEqualTo("member1");
    }

    @Test
    public void resultFetch() throws Exception {
        // 1) Given
        List<Member> members = queryFactory.selectFrom(member).fetch();

        Member member1 = queryFactory.selectFrom(member).fetchOne();

        Member member2 = queryFactory.selectFrom(member).fetchFirst();

        // Mind that for any scenario where the count is not strictly needed separately,
        // we recommend to use fetch() instead.
        QueryResults<Member> memberQueryResults = queryFactory.selectFrom(member).fetchResults();
        memberQueryResults.getTotal();
        List<Member> results = memberQueryResults.getResults();

        // 2) When

        // 3) Then

    }

    /**
     * 회원 정렬 순서
     * 1. 회원 나이 내림차순
     * 2. 회원 이름 올림차순
     * 단 2번에서 회원 이름이 없으면 마지막에 출력(이름이 없으면 맨 마지막으로)
     */
    @Test
    public void sort() throws Exception {
        // 1) Given
        entityManager.persist(new Member(null, 100));
        entityManager.persist(new Member("member5", 100));
        entityManager.persist(new Member("member6", 100));

        // 2) When
        List<Member> members = queryFactory
                .selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast())
                .fetch();

        // 3) Then
        Member member5 = members.get(0);
        Member member6 = members.get(1);
        Member memberNull = members.get(2);

        assertThat(member5.getUsername()).isEqualTo("member5");
        assertThat(member6.getUsername()).isEqualTo("member6");
        assertThat(memberNull.getUsername()).isNull();
    }

    @Test
    public void paging1() throws Exception {
        // 1) Given
        List<Member> members = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(2)
                .limit(2)
                .fetch();

        // 2) When


        // 3) Then
        for (Member member1 : members) {
            System.out.println("member1 = " + member1);
        }

        assertThat(members.size()).isEqualTo(2);
    }

    @Test
    public void aggregation() throws Exception {
        // 1) Given
        // > 실제로는 tuple 로 안쓰고 DTO 로 뽑아온다.
        List<Tuple> tuples = queryFactory
                .select(
                        member.count(),
                        member.age.sum(),
                        member.age.avg(),
                        member.age.max(),
                        member.age.min()
                )
                .from(member)
                .fetch();

        // 2) When
        Tuple tuple = tuples.get(0);

        // 3) Then
        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(100);
        assertThat(tuple.get(member.age.avg())).isEqualTo(25);
        assertThat(tuple.get(member.age.max())).isEqualTo(40);
        assertThat(tuple.get(member.age.min())).isEqualTo(10);
    }

    /**
     * 팀의 이름과 각 팀의 평균 연령을 구해라
     */
    @Test
    public void group() throws Exception {
        // 1) Given
        List<Tuple> tuples = queryFactory
                .select(
                        team.name,
                        member.age.avg()
                )
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
                .fetch();

        // 2) When
        Tuple teamATuple = tuples.get(0);
        Tuple teamBTuple = tuples.get(1);

        // 3) Then
        assertThat(teamATuple.get(team.name)).isEqualTo("teamA");
        assertThat(teamATuple.get(member.age.avg())).isEqualTo(15);

        assertThat(teamBTuple.get(team.name)).isEqualTo("teamB");
        assertThat(teamBTuple.get(member.age.avg())).isEqualTo(35);
    }

    /**
     * 팀 A 에 소속된 모든 회원
     */
    @Test
    public void join() throws Exception {
        // 1) Given
        List<Member> members = queryFactory
                .selectFrom(member)
                .leftJoin(member.team, team)
                //.join(member.team, team)
                .where(team.name.eq("teamA"))
                .fetch();

        // 2) When

        // 3) Then
        assertThat(members)
                .extracting("username")
                .containsExactly("member1", "member2");
    }

    /**
     * 세타 조인 (막 조인 == 연관관계가 없는 조인, NoSQL 느낌)
     *
     * 회원의 이름이 팀 이름과 같은 회원을 조회
     */
    @Test
    public void theta_join() throws Exception {
        // 1) Given
        entityManager.persist(new Member("teamA"));
        entityManager.persist(new Member("teamB"));
        entityManager.persist(new Member("teamC"));

        // 2) When
        List<Member> members = queryFactory
                .select(member)
                .from(member, team)
                .where(member.username.eq(team.name))
                .fetch();

        // 3) Then
        assertThat(members)
                .extracting("username")
                .containsExactly("teamA", "teamB");
    }

    /**
     * 회원과 팀을 조인하면서, 팀 이름이 teamA 인 팀만 조인, 회원은 모두 조회
     * JPQL : select m from Member m left join m.team t on t.name = "teamA"
     */
    @Test
    public void join_on_filtering() throws Exception {
        // 1) Given
        List<Tuple> teamA = queryFactory
                .select(member, team)
                .from(member)

                // 내부 조인이면 where 절로 필터링
                /*.join(member.team, team)
                .where(team.name.eq("teamA"))*/

                // 외부 조인이면 on 절로 필터링
                .leftJoin(member.team, team)
                .on(team.name.eq("teamA"))

                .fetch();

        // 2) When

        // 3) Then
        // Left outer join
        for (Tuple tuple : teamA) {
            System.out.println("tuple = " + tuple);
        }
        /**
         * tuple = [Member{id=3, username='member1', age=10}, Team{id=1, name='teamA'}]
         * tuple = [Member{id=4, username='member2', age=20}, Team{id=1, name='teamA'}]
         * tuple = [Member{id=5, username='member3', age=30}, null]
         * tuple = [Member{id=6, username='member4', age=40}, null]
         */
    }

    /**
     * 연관관계가 없는 엔티티 외부 조인
     *
     * 회원의 이름이 팀 이름과 같은 대상 외부 조인
     */
    @Test
    public void join_on_no_relation() throws Exception {
        // 1) Given
        entityManager.persist(new Member("teamA"));
        entityManager.persist(new Member("teamB"));
        entityManager.persist(new Member("teamC"));

        // 2) When
        List<Tuple> members = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(team)
                .on(member.username.eq(team.name))
                .fetch();

        // 3) Then
        for (Tuple tuple : members) {
            System.out.println("tuple = " + tuple);
        }
    }

    @PersistenceUnit
    EntityManagerFactory entityManagerFactory;

    @Test
    public void fetchJoinNo() throws Exception {
        // 1) Given
        entityManager.flush();
        entityManager.clear();

        // 2) When
        // Member 만 조회됨 > Team 은 Lazy 상태임
        Member member1 = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        // 3) Then
        boolean loaded = entityManagerFactory.getPersistenceUnitUtil().isLoaded(member1.getTeam());
        assertThat(loaded).as("패치 조인이 미적용이면 Team 은 영속성 컨텍스트에 로딩되면 안된다.").isFalse();
    }

    @Test
    public void fetchJoinUse() throws Exception {
        // 1) Given
        entityManager.flush();
        entityManager.clear();

        // 2) When
        // Member 만 조회됨 > Team 은 Lazy 상태임
        Member member1 = queryFactory
                .selectFrom(member)
                .join(member.team, team).fetchJoin()
                .where(member.username.eq("member1"))
                .fetchOne();

        // 3) Then
        boolean loaded = entityManagerFactory.getPersistenceUnitUtil().isLoaded(member1.getTeam());
        assertThat(loaded).as("패치 조인이 적용되면 Team 은 영속성 컨텍스트에 로딩되어야 한다.").isTrue();
    }

    /**
     * 나이가 가장 많은 회원 조회
     */
    @Test
    public void subQueryEq() throws Exception {
        // 1) Given
        QMember memberSub = new QMember("memberSub");

        // 2) When
        List<Member> members = queryFactory
                .selectFrom(member)
                .where(member.age.eq(
                        // SUB QUERY
                        select(memberSub.age.max())
                                .from(memberSub)
                        // SUB QUERY
                ))
                .fetch();

        // 3) Then
        assertThat(members)
                .extracting("age")
                .containsExactly(40);
    }

    /**
     * 나이가 평균 이상인 회원 조회
     */
    @Test
    public void subQueryGoe() throws Exception {
        // 1) Given
        QMember memberSub = new QMember("memberSub");

        // 2) When
        List<Member> members = queryFactory
                .selectFrom(member)
                .where(member.age.goe(
                        // SUB QUERY
                        select(memberSub.age.avg())
                                .from(memberSub)
                        // SUB QUERY
                ))
                .fetch();

        // 3) Then
        assertThat(members)
                .extracting("age")
                .containsExactly(30, 40);
    }

    /**
     * 나이가 10살 이상인 회원 조회
     */
    @Test
    public void subQueryIn() throws Exception {
        // 1) Given
        QMember memberSub = new QMember("memberSub");

        // 2) When
        List<Member> members = queryFactory
                .selectFrom(member)
                .where(member.age.in(
                        // SUB QUERY
                        select(memberSub.age)
                                .from(memberSub)
                        // SUB QUERY
                                .where(memberSub.age.gt(10))
                ))
                .fetch();

        // 3) Then
        assertThat(members)
                .extracting("age")
                .containsExactly(20, 30, 40);
    }

    /**
     * 회원 이름과 평균 나이 같이 출력
     */
    @Test
    public void selectSubQuery() throws Exception {
        // 1) Given
        QMember memberSub = new QMember("memberSub");
        //QMember memberSub2 = new QMember("memberSub2");

        // 2) When
        List<Tuple> members = queryFactory
                .select(member.username,
                        select(memberSub.age.avg())
                                .from(memberSub)
                        /**
                         * ! From 절의 Sub query 는 JPA 에서 지원하지 않기 때문에 동작하지 않는다.
                         * - 하이버네이트에서는 지원한다.
                         *
                         * from(
                         * select(memberSub2)
                         *      .from(memberSub2)
                         *      .where(memberSub2.username.eq("member2"))
                         *      )
                         */
                )
                .from(member)
                .fetch();

        // 3) Then
        for (Tuple tuple : members) {
            System.out.println("tuple = " + tuple);
        }
        /**
         * tuple = [member1, 25.0]
         * tuple = [member2, 25.0]
         * tuple = [member3, 25.0]
         * tuple = [member4, 25.0]
         */
    }

    /**
     * Case 로직은 DB 에서 하는 것 보다는 어플리케이션 레벨에서 처리하는게 맞다
     * 진짜 필요한 로직이 아니면 쓰지말자!
     */
    @Test
    public void basicCase() throws Exception {
        // 1) Given

        // 2) When
        List<String> result = queryFactory
                .select(member.age
                        .when(10).then("열살")
                        .when(20).then("스무살")
                        .otherwise("기타")
                )
                .from(member)
                .fetch();

        // 3) Then
        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void complexCase() throws Exception {
        // 1) Given

        // 2) When
        List<String> result = queryFactory
                .select(
                        new CaseBuilder()
                                .when(member.age.between(0, 20)).then("0~20살")
                                .when(member.age.between(21, 30)).then("21~30살")
                                .otherwise("기타")
                )
                .from(member)
                .fetch();

        // 3) Then
        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void constant() throws Exception {
        // 1) Given

        // 2) When
        List<Tuple> result = queryFactory
                .select(member.username,
                        Expressions.constant("A")
                )
                .from(member)
                .fetch();

        // 3) Then
        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @Test
    public void concat() throws Exception {
        // 1) Given

        // 2) When
        List<String> result = queryFactory
                .select(member.username.concat("_")
                        .concat(member.age.stringValue())
                        /**
                         * member.age.stringValue()
                         *
                         * 문자가 아닌 다른 타입들은 stringValue() 로 문자로 변환할 수 있다.
                         * 이 방법은 ENUM 을 처리할 때도 자주 사용한다.
                         */
                )
                .from(member)
                .where(member.username.eq("member1"))
                .fetch();

        // 3) Then
        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    /**
     * Projection : select 문에 나열하는 것 (object, string, integer 등)
     */
    @Test
    public void simpleProjection() throws Exception {
        // 1) Given

        // 2) When
        List<Member> result = queryFactory
                .select(member)
                //.select(member.username)
                .from(member)
                .fetch();

        // 3) Then
        for (Member member1 : result) {
            System.out.println("member1 = " + member1);
        }
    }

    /**
     * Tuple 은 package com.querydsl.core; 에 속해있기 때문에
     * 앞단으로 넘어가면 안좋다.
     * 즉, repository 레벨에서만 다뤄야 한다.
     * service, view, controller 에서는 사용하면 안되도록 설계해야 한다!
     *
     * 그래서 tuple 은 dto 로 바꿔서 반환해야 한다!!
     */
    @Test
    public void tupleProjection() throws Exception {
        // 1) Given

        // 2) When
        List<Tuple> result = queryFactory
                .select(member.username, member.age)
                .from(member)
                .fetch();

        // 3) Then
        for (Tuple tuple : result) {
            String username = tuple.get(member.username);
            System.out.println("username = " + username);
            Integer age = tuple.get(member.age);
            System.out.println("age = " + age);
        }
    }

    @Test
    public void findDtoByJPQL() throws Exception {
        // 1) Given

        // 2) When
        List<MemberDto> result = entityManager
                .createQuery(
                        /**
                         * 패키지명을 언제 다적고 있냐고... 너무 더럽다 더러워 아으
                         */
                        "select new study.querydsl.entity.dto.MemberDto(m.username, m.age) from Member m",
                        MemberDto.class
                )
                .getResultList();

        // 3) Then
        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * # Bean Population
     * - 결과를 DTO 로 반환할 때 사용
     *
     * 1. Property 접근 (setter)
     * 2. 필드 직접 접근
     * 3. 생성자 사용
     *
     */
    @Test
    public void findDtoBySetter() throws Exception {
        // 1) Given

        // 2) When
        List<MemberDto> result = queryFactory
                .select(
                        /**
                         * 그냥 놀랍다 놀라워 아으
                         * 순서 상관없다.
                         */
                        Projections.bean(
                                MemberDto.class,
                                member.age,
                                member.username
                        ) // Setter 라서 기본 생성자가 정의 되어 있어야 한다.
                )
                .from(member)
                .fetch();

        // 3) Then
        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    public void findDtoByField() throws Exception {
        // 1) Given

        // 2) When
        List<MemberDto> result = queryFactory
                .select(
                        /**
                         * 클래스 내부 필드에 바로 꽂아버린다 아으
                         * 순서 상관없다.
                         */
                        Projections.fields(
                                MemberDto.class,
                                member.age,
                                member.username
                        )
                )
                .from(member)
                .fetch();

        // 3) Then
        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    public void findDtoByConstructor() throws Exception {
        // 1) Given

        // 2) When
        List<MemberDto> result = queryFactory
                .select(
                        /**
                         * 생성자 타입을 잘 맞춰야 한다.
                         * 순서 맞춰야 한다.
                         */
                        Projections.constructor(
                                MemberDto.class,
                                member.username,
                                member.age
                        )
                )
                .from(member)
                .fetch();

        // 3) Then
        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    public void findUserDtoByField() throws Exception {
        // 1) Given
        QMember memberSub = new QMember("memberSub");

        // 2) When
        List<UserDto> result = queryFactory
                .select(
                        /**
                         * 필드 이름이 안맞으면 매칭되는게 없어서 무시되서 값이 안들어간다.
                         *
                         * userDto = UserDto(name=null, age=10)
                         * userDto = UserDto(name=null, age=20)
                         * userDto = UserDto(name=null, age=30)
                         * userDto = UserDto(name=null, age=40)
                         *
                         * 그래서 .as("name") 으로 해줘야 한다.
                         */
                        Projections.fields(
                                UserDto.class,
                                /**
                                 * SubQuery 사용할 경우에는 ExpressionUtils 로 감싸야 한다.
                                 */
                                ExpressionUtils.as(
                                        /**
                                         * userDto = UserDto(name=member1, age=10)
                                         * userDto = UserDto(name=member1, age=20)
                                         * userDto = UserDto(name=member1, age=30)
                                         * userDto = UserDto(name=member1, age=40)
                                         */
                                        JPAExpressions
                                                .select(memberSub.username)
                                                .from(memberSub)
                                                .where(memberSub.username.eq("member1")),
                                        "name"
                                ),
                                //member.username.as("name"),
                                //member.username,
                                member.age
                                /*ExpressionUtils.as(
                                        JPAExpressions
                                                .select(memberSub.age.max())
                                                .from(memberSub),
                                        "age"
                                )*/
                        )
                )
                .from(member)
                .fetch();

        // 3) Then
        for (UserDto userDto : result) {
            System.out.println("userDto = " + userDto);
        }
    }

    @Test
    public void findUserDtoByConstructor() throws Exception {
        // 1) Given

        // 2) When
        List<UserDto> result = queryFactory
                .select(
                        /**
                         * 생성자 타입을 잘 맞춰야 한다.
                         * 순서 맞춰야 한다.
                         * 필드 이름과 상관 없다!
                         */
                        Projections.constructor(
                                UserDto.class,
                                member.username,
                                member.age
                        )
                )
                //.distinct()
                .from(member)
                .fetch();

        // 3) Then
        for (UserDto userDto : result) {
            System.out.println("userDto = " + userDto);
        }
    }

    /**
     * Constructor 안쓰고 QueryProjection 쓰는 이유는? 차이점은?
     *
     * > Constructor 사용하면 생성자에 타입이 안맞는 값을 넣을 때 컴파일 타임 오류를 잡아낼 수 없다.
     *   런타임 오류로만 확인이 가능하다.
     *
     * > QueryProjection 에서는 바로 컴파일 타임 오류로 확인된다.
     * 근데 단점이라고 하면, Q File 을 생성해야하는 것이다.
     * 그리고 DTO 클래스가 QueryDSL 라이브러리에 의존된다.
     * 이 DTO 가 repository 말고 서비스나 컨트롤러에서 넘어가서 사용될 수 있는데,
     * QueryDSL 에 의존적인 설계로 인해 DTO 가 사용되는 서비스나 컨트롤러 코드가 QueryDSL 에 종속적이게 된다.
     * 순수하지 않은 POJO 객체가 되는 것이다.
     * > 설계를 잘해볼 필요가 있다...
     *
     */
    @Test
    public void findDtoQueryProjection() throws Exception {
        // 1) Given

        // 2) When
        List<MemberDto> result = queryFactory
                .select(
                        new QMemberDto(member.username, member.age)
                )
                .from(member)
                .fetch();

        // 3) Then
        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * 동적 쿼리
     * Q1) Member1 인 유저 이름과 나이가 10살인 멤버를 찾고 싶다.
     */
    @Test
    public void dynamicQuery_BooleanBuilder() throws Exception {
        // 1) Given
        String usernameParam = "member1";
        Integer ageParam = null;
        //Integer ageParam = 10;

        // 2) When
        List<Member> result = searchMember1(usernameParam, ageParam);

        // 3) Then
        assertThat(result).hasSize(1);
    }

    private List<Member> searchMember1(String usernameCond, Integer ageCond) {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        if (usernameCond != null && !usernameCond.isEmpty()) {
            booleanBuilder.and(member.username.eq(usernameCond));
        }

        if (ageCond != null && ageCond > 0) {
            booleanBuilder.and(member.age.eq(ageCond));
        }

        return queryFactory
                .selectFrom(member)
                .where(booleanBuilder)
                .fetch();
    }

    /**
     * BooleanBuilder 보다 더 개발자 친화적이다.
     * 함수로 코드의 의미를 바로 파악할 수 있기 때문에 더 가독성이 높다.
     */
    @Test
    public void dynamicQuery_WhereParam() throws Exception {
        // 1) Given
        String usernameParam = "member1";
        Integer ageParam = null;
        //Integer ageParam = 10;

        // 2) When
        List<Member> result = searchMember2(usernameParam, ageParam);

        // 3) Then
        assertThat(result).hasSize(1);
    }

    private List<Member> searchMember2(String usernameCond, Integer ageCond) {
        return queryFactory
                .selectFrom(member)
                .where(
                        /**
                         * 함수 리턴값이 null 이면 where 절에서 무시된다.
                         */
                        isAllInfoEqual(usernameCond, ageCond)
                        /*isUsernameEqual(usernameCond),
                        isAgeEqual(ageCond)*/
                )
                .fetch();
    }

    //private Predicate isUsernameEqual(String usernameCond) {
    private BooleanExpression isUsernameEqual(String usernameCond) {
        if (hasText(usernameCond)) { return null; }
        return member.username.eq(usernameCond);
    }

    private BooleanExpression isAgeEqual(Integer ageCond) {
        if (ageCond == null || ageCond <= 0) {
            return null;
        }
        return member.age.eq(ageCond);
    }

    /**
     * 이렇게 함수로 사용하게 되면 조립(조합)을 할 수 있다. 재사용도 할 수 있다.
     */
    private BooleanExpression isAllInfoEqual(String usernameCond, Integer ageCond) {
        return Objects.requireNonNull(
                Objects.requireNonNull(isUsernameEqual(usernameCond))
        ).and(isAgeEqual(ageCond));
    }

    @Test
    public void bulkUpdate() throws Exception {
        // 1) Given

        // 2) When
        /**
         * JPQL 을 사용하므로 자동으로 Update 실행 시 영속성 컨텍스트가 flush 된다.
         */
        long resultRowNum = queryFactory
                .update(member)
                .set(member.username, "비회원")
                .where(member.age.lt(28))
                .execute();

        /**
         * 벌크 업데이트 적용 후에 쿼리로 데이터 조회하면 잘나오지만,
         * 만약에 벌크 연산을 때리고 트랜잭션이 끝나지 않은 상태에서 영속성 컨텍스트에서 값을 가져오면 데이터 정합성이 맞지 않는다.
         * 그 이유는 JPA 영속성 컨텍스트는 이미 1차 캐시로 조회한 객체가 존재하면 DB 에서 가져온 값을 버린다. (영속성 컨텍스트에 있는 값이 우선이다.)
         *
         * > 근데 왜 반영이 잘되지? 하이버네이트 버전이 업데이트되서 그런가?...
         */
        Member member1 = entityManager.find(
                Member.class,
                3L
        );
        System.out.println("member1 = " + member1);
        /**
         * > member1 = Member{id=3, username='비회원', age=10}
         *
         * member1 = Member{id=3, username='member1', age=10} 이 아님...
         */

        /**
         * 초기화를 해줘야 한다.
         *
         * 엔티티 매니저의 SQL저장소에 혹시라도 남아있을지 모를 쿼리를 실행하기 위해 flush()를 같이 실행한다.
         */
        //entityManager.flush();
       // entityManager.clear();

        // 3) Then
        /**
         * member1 = 10 -> 비회원
         * member2 = 20 -> 비회원
         * member3 = 30 -> member3
         * member4 = 40 -> member4
         */
        assertThat(resultRowNum).isEqualTo(2);

        for (Member fetch : queryFactory
                // selectFrom을 사용하게 되면 JPQL이 실행된다. JPQL은 실행직전에 플러시를 호출한다.
                .selectFrom(member)
                .fetch()) {
            System.out.println("fetch = " + fetch);
        }
        /**
         * fetch = Member{id=3, username='비회원', age=10}
         * fetch = Member{id=4, username='비회원', age=20}
         * fetch = Member{id=5, username='member3', age=30}
         * fetch = Member{id=6, username='member4', age=40}
         */
    }

    @Test
    public void bulkAdd() throws Exception {
        // 1) Given
        System.out.println("BEFORE");
        for (Member fetch : queryFactory
                .selectFrom(member)
                .fetch()) {
            System.out.println("fetch = " + fetch);
        }
        /**
         * fetch = Member{id=3, username='member1', age=10}
         * fetch = Member{id=4, username='member2', age=20}
         * fetch = Member{id=5, username='member3', age=30}
         * fetch = Member{id=6, username='member4', age=40}
         */
        System.out.println("BEFORE");


        // 2) When
        long count = queryFactory
                .update(member)
                .set(member.age, member.age.add(1))
                //.set(member.age, member.age.multiply(2))
                .execute();

        entityManager.flush();
        entityManager.clear();

        // 3) Then
        assertThat(count).isEqualTo(4);

        System.out.println("-----------------------------");
        System.out.println("AFTER");
        for (Member fetch : queryFactory
                .selectFrom(member)
                .fetch()) {
            System.out.println("fetch = " + fetch);
        }
        /**
         * fetch = Member{id=3, username='member1', age=11}
         * fetch = Member{id=4, username='member2', age=21}
         * fetch = Member{id=5, username='member3', age=31}
         * fetch = Member{id=6, username='member4', age=41}
         */
        System.out.println("AFTER");
    }

    @Test
    public void bulkDelete() throws Exception {
        // 1) Given
        for (Member fetch : queryFactory
                .selectFrom(member)
                .fetch()) {
            System.out.println("fetch = " + fetch);
        }
        /**
         * fetch = Member{id=3, username='member1', age=10}
         * fetch = Member{id=4, username='member2', age=20}
         * fetch = Member{id=5, username='member3', age=30}
         * fetch = Member{id=6, username='member4', age=40}
         */

        // 2) When
        long count = queryFactory
                .delete(member)
                .where(member.age.gt(10))
                .execute();

        entityManager.flush();
        entityManager.clear();

        // 3) Then
        assertThat(count).isEqualTo(3);

        for (Member fetch : queryFactory
                .selectFrom(member)
                .fetch()) {
            System.out.println("fetch = " + fetch);
        }
        /**
         * fetch = Member{id=3, username='member1', age=10}
         */
    }

    /**
     * 멤버들의 이름에서 member 라는 문자열을 M 으로 바꾼다.
     */
    @Test
    public void sqlFunctionReplace() throws Exception {
        // 1) Given

        // 2) When
        List<String> result = queryFactory
                .select(
                        Expressions.stringTemplate(
                                "function('replace', {0}, {1}, {2})",
                                member.username, "member", "M"
                        )
                )
                .from(member)
                .fetch();

        // 3) Then
        for (String s : result) {
            System.out.println("s = " + s);
        }
        /**
         * s = M1
         * s = M2
         * s = M3
         * s = M4
         */
    }

    @Test
    public void sqlFunctionLower() throws Exception {
        // 1) Given

        // 2) When
        // 예제가 썩 좋지는 않다.
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
                /*.where(
                        member.username.eq(
                                Expressions.stringTemplate(
                                        "function('lower', {0})",
                                        member.username
                                )
                        )
                )*/
                .where(
                        // QueryDSL 에서는 ANSI 표준 함수를 거의 다 제공한다.
                        member.username.eq(member.username.lower())
                )
                .fetch();

        // 3) Then
        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

}
