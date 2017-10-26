package org.sunbird.learner.actors;

import static akka.testkit.JavaTestKit.duration;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.datasecurity.OneWayHashing;
import org.sunbird.common.request.Request;
import org.sunbird.common.util.Util;
import org.sunbird.helper.ServiceFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.TestActorRef;
import akka.testkit.javadsl.TestKit;

/**
 * @author  arvind
 */
public class LearnerStateActorTest {

    static ActorSystem system;
    final static Props props = Props.create(LearnerStateActor.class);
    static TestActorRef<LearnerStateActor> ref;
    private static CassandraOperation cassandraOperation = ServiceFactory.getInstance();
    private static Util.DbInfo contentdbInfo = Util.dbInfoMap.get(JsonKey.LEARNER_CONTENT_DB);
    private static Util.DbInfo coursedbInfo = Util.dbInfoMap.get(JsonKey.LEARNER_COURSE_DB);
    static String userId = "user121gama";
    static String courseId = "alpha01crs12";
    static String batchId ="115";
    private static final String contentId = "cont3544TeBuk";

    @BeforeClass
    public static void setUp() {
        system = ActorSystem.create("system");
        Util.checkCassandraDbConnections();
        ref = TestActorRef.create(system, props, "testActor");
        insertCourse();
        insertContent();
    }

    private static void insertContent() {

        Map<String , Object> contentMap = new HashMap<String , Object>();
        String key = userId + JsonKey.PRIMARY_KEY_DELIMETER + contentId + JsonKey.PRIMARY_KEY_DELIMETER
            + courseId+JsonKey.PRIMARY_KEY_DELIMETER+batchId;
        String id = OneWayHashing.encryptVal(key);
        contentMap.put(JsonKey.ID , id);
        contentMap.put(JsonKey.COURSE_ID , courseId);
        contentMap.put(JsonKey.USER_ID , userId);
        contentMap.put(JsonKey.CONTENT_ID , contentId);
        contentMap.put(JsonKey.BATCH_ID , batchId);
        System.out.println("CONTENT ID "+id);
        cassandraOperation.insertRecord(contentdbInfo.getKeySpace() , contentdbInfo.getTableName() , contentMap);

    }

    private static void insertCourse() {
        Map<String , Object> courseMap = new HashMap<String , Object>();
        courseMap.put(JsonKey.ID , OneWayHashing.encryptVal(userId+JsonKey.PRIMARY_KEY_DELIMETER+courseId+JsonKey.PRIMARY_KEY_DELIMETER+batchId));
        courseMap.put(JsonKey.COURSE_ID , courseId);
        courseMap.put(JsonKey.USER_ID , userId);
        courseMap.put(JsonKey.CONTENT_ID , courseId);
        courseMap.put(JsonKey.BATCH_ID , batchId);
        cassandraOperation.insertRecord(coursedbInfo.getKeySpace() , coursedbInfo.getTableName() , courseMap);
    }

    //@Test
    public void onReceiveTestForGetCourse() throws Exception {

        TestKit probe = new TestKit(system);
        ActorRef subject = system.actorOf(props);

        Request request = new Request();
        Map<String, Object> map = new HashMap<>();
        map.put(JsonKey.USER_ID, userId);
        request.setRequest(map);
        request.setManagerName(ActorOperations.GET_COURSE.getKey());
        request.setOperation(ActorOperations.GET_COURSE.getValue());
        subject.tell(request, probe.getRef());
        Response res= probe.expectMsgClass(duration("10 second"),Response.class);
        List list = (List) res.getResult().get(JsonKey.RESPONSE);
        Assert.assertEquals(1 , list.size());

    }

    @Test
    public void onReceiveTestForGetCourseWithInvalidOperation() throws Exception {

        TestKit probe = new TestKit(system);
        ActorRef subject = system.actorOf(props);
        Request request = new Request();
        Map<String, Object> map = new HashMap<>();
        map.put(JsonKey.USER_ID, userId);
        request.setRequest(map);
        request.setManagerName("INVALID_OPERATION");
        request.setOperation("INVALID_OPERATION");
        subject.tell(request, probe.getRef());
        probe.expectMsgClass(ProjectCommonException.class);

    }

    @Test
    public void onReceiveTestForGetCourseWithInvalidRequestType() throws Exception {

        TestKit probe = new TestKit(system);
        ActorRef subject = system.actorOf(props);

        subject.tell("INVALID REQUEST", probe.getRef());
        probe.expectMsgClass(ProjectCommonException.class);

    }

    @Test
    public void onReceiveTestForGetContent() throws Exception {

        TestKit probe = new TestKit(system);
        ActorRef subject = system.actorOf(props);
        HashMap<String, Object> innerMap = new HashMap<>();
        Request request = new Request();
        innerMap.put(JsonKey.USER_ID, userId);
        List<String> contentList = Arrays.asList(contentId);
        innerMap.put(JsonKey.CONTENT_IDS, contentList);
        request.setRequest(innerMap);
        request.setManagerName(ActorOperations.GET_CONTENT.getKey());
        request.setOperation(ActorOperations.GET_CONTENT.getValue());
        subject.tell(request, probe.getRef());
        Response res= probe.expectMsgClass(duration("10 second"),Response.class);
        List list = (List) res.getResult().get(JsonKey.RESPONSE);
        Assert.assertEquals(1 , list.size());
    }

    @Test
    public void onReceiveTestForGetContentByCourse() throws Exception {

        TestKit probe = new TestKit(system);
        ActorRef subject = system.actorOf(props);
        HashMap<String, Object> innerMap = new HashMap<>();
        Request request = new Request();
        innerMap.put(JsonKey.USER_ID, userId);
        innerMap.put(JsonKey.COURSE_ID , courseId);
        List<String> contentList = Arrays.asList(contentId);
        innerMap.put(JsonKey.CONTENT_IDS, contentList);
        innerMap.put(JsonKey.COURSE, innerMap);
        request.setRequest(innerMap);
        request.setManagerName(ActorOperations.GET_CONTENT.getKey());
        request.setOperation(ActorOperations.GET_CONTENT.getValue());
        subject.tell(request, probe.getRef());
        Response res= probe.expectMsgClass(duration("10 second"),Response.class);
        List list = (List) res.getResult().get(JsonKey.RESPONSE);
        Assert.assertEquals(1 , list.size());
    }

    @Test
    public void onReceiveTestForGetContentByCourseIds() throws Exception {

        TestKit probe = new TestKit(system);
        ActorRef subject = system.actorOf(props);
        HashMap<String, Object> innerMap = new HashMap<>();
        Request request = new Request();
        innerMap.put(JsonKey.USER_ID, userId);
        List<String> courseList = Arrays.asList(courseId);
        innerMap.put(JsonKey.COURSE_IDS, courseList);
        request.setRequest(innerMap);
        request.setManagerName(ActorOperations.GET_CONTENT.getKey());
        request.setOperation(ActorOperations.GET_CONTENT.getValue());
        subject.tell(request, probe.getRef());
        Response res= probe.expectMsgClass(duration("10 second"),Response.class);
        List list = (List) res.getResult().get(JsonKey.RESPONSE);
        Assert.assertEquals(1 , list.size());
    }


    @AfterClass
    public static void destroy(){
        cassandraOperation.deleteRecord(coursedbInfo.getKeySpace() , coursedbInfo.getTableName(),OneWayHashing.encryptVal(userId+JsonKey.PRIMARY_KEY_DELIMETER+courseId+JsonKey.PRIMARY_KEY_DELIMETER+batchId));
        String key = userId + JsonKey.PRIMARY_KEY_DELIMETER + contentId + JsonKey.PRIMARY_KEY_DELIMETER
            + courseId+JsonKey.PRIMARY_KEY_DELIMETER+batchId;
        String contentid = OneWayHashing.encryptVal(key);
        System.out.println("CONTENT ID "+contentid);
        cassandraOperation.deleteRecord(contentdbInfo.getKeySpace() , contentdbInfo.getTableName(),contentid);
    }

}
