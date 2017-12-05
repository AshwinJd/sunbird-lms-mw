package org.sunbird.learner.util;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.ElasticSearchUtil;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.ConfigUtil;
import org.sunbird.common.models.util.HttpUtil;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.models.util.ProjectUtil.EsIndex;
import org.sunbird.common.models.util.ProjectUtil.EsType;
import org.sunbird.common.models.util.ProjectUtil.OrgStatus;
import org.sunbird.common.quartz.scheduler.SchedulerManager;
import org.sunbird.dto.SearchDTO;
import org.sunbird.helper.CassandraConnectionManager;
import org.sunbird.helper.CassandraConnectionMngrFactory;
import org.sunbird.helper.ServiceFactory;

import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * Utility class for actors
 * @author arvind .
 */
public class Util {

    public static Map<String, DbInfo> dbInfoMap = new HashMap<>();
    public static final int RECOMENDED_LIST_SIZE = 10;
    
    private static final String KEY_SPACE_NAME = "sunbird";
    private static Properties prop = new Properties();
    private static Map<String, String> headers = new HashMap<>();
    private static Map<Integer , List<Integer>> orgStatusTransition = new HashMap<>();
    public static final Map<String,Object> auditLogUrlMap  = new HashMap<>();
   // private static final AuditOperation auditOperation = null;
    
    
    static {
        initializeOrgStatusTransition();
        initializeDBProperty();
        initializeAuditLogUrl();
        // EkStep HttpClient headers init
        headers.put("content-type", "application/json");
        headers.put("accept", "application/json");
        new Thread(new Runnable() {
          @Override
          public void run() {
            SchedulerManager.getInstance();
          }
        }).start();
       
    }

    /**
     * This method will a map of organization state transaction. 
     * which will help us to move the organization status from one 
     * Valid state to another state.
     */
    private static void initializeOrgStatusTransition() {
        orgStatusTransition.put(OrgStatus.ACTIVE.getValue(), Arrays.asList(OrgStatus.ACTIVE.getValue(),OrgStatus.INACTIVE.getValue(),OrgStatus.BLOCKED.getValue(),OrgStatus.RETIRED.getValue()));
        orgStatusTransition.put(OrgStatus.INACTIVE.getValue() , Arrays.asList(OrgStatus.ACTIVE.getValue(),OrgStatus.INACTIVE.getValue()));
        orgStatusTransition.put(OrgStatus.BLOCKED.getValue() ,Arrays.asList(OrgStatus.ACTIVE.getValue(),OrgStatus.BLOCKED.getValue(),OrgStatus.RETIRED.getValue()));
        orgStatusTransition.put(OrgStatus.RETIRED.getValue() ,Arrays.asList(OrgStatus.RETIRED.getValue()));
    }
    
    private static void initializeAuditLogUrl() {
      //This map will hold ActorOperationType as key and AuditOperation Object as value which contains operation Type  and Object Type info.
      auditLogUrlMap.put(ActorOperations.CREATE_USER.getValue(), new AuditOperation(JsonKey.USER, JsonKey.CREATE));
      auditLogUrlMap.put(ActorOperations.UPDATE_USER.getValue(), new AuditOperation(JsonKey.USER, JsonKey.UPDATE));
      auditLogUrlMap.put(ActorOperations.CREATE_ORG.getValue(), new AuditOperation(JsonKey.ORGANISATION, JsonKey.CREATE));
      auditLogUrlMap.put(ActorOperations.UPDATE_ORG.getValue(), new AuditOperation(JsonKey.ORGANISATION, JsonKey.UPDATE));
      auditLogUrlMap.put(ActorOperations.UPDATE_ORG_STATUS.getValue(), new AuditOperation(JsonKey.ORGANISATION, JsonKey.UPDATE));
      auditLogUrlMap.put(ActorOperations.APPROVE_ORG.getValue(), new AuditOperation(JsonKey.ORGANISATION, JsonKey.UPDATE));
      auditLogUrlMap.put(ActorOperations.APPROVE_ORGANISATION.getValue(), new AuditOperation(JsonKey.ORGANISATION, JsonKey.UPDATE));
      auditLogUrlMap.put(ActorOperations.JOIN_USER_ORGANISATION.getValue(), new AuditOperation(JsonKey.ORGANISATION, JsonKey.UPDATE));
      auditLogUrlMap.put(ActorOperations.ADD_MEMBER_ORGANISATION.getValue(), new AuditOperation(JsonKey.ORGANISATION, JsonKey.UPDATE));
      auditLogUrlMap.put(ActorOperations.REMOVE_MEMBER_ORGANISATION.getValue(), new AuditOperation(JsonKey.ORGANISATION, JsonKey.UPDATE));
      auditLogUrlMap.put(ActorOperations.APPROVE_USER_ORGANISATION.getValue(), new AuditOperation(JsonKey.ORGANISATION, JsonKey.UPDATE));
      auditLogUrlMap.put(ActorOperations.REJECT_USER_ORGANISATION.getValue(), new AuditOperation(JsonKey.ORGANISATION, JsonKey.UPDATE));
      auditLogUrlMap.put(ActorOperations.BLOCK_USER.getValue(), new AuditOperation(JsonKey.USER, JsonKey.UPDATE));
      auditLogUrlMap.put(ActorOperations.UNBLOCK_USER.getValue(), new AuditOperation(JsonKey.USER, JsonKey.UPDATE));
      auditLogUrlMap.put(ActorOperations.ASSIGN_ROLES.getValue(), new AuditOperation(JsonKey.USER, JsonKey.UPDATE));
      auditLogUrlMap.put(ActorOperations.CREATE_BATCH.getValue(), new AuditOperation(JsonKey.BATCH, JsonKey.CREATE));
      auditLogUrlMap.put(ActorOperations.UPDATE_BATCH.getValue(), new AuditOperation(JsonKey.BATCH, JsonKey.UPDATE));
      auditLogUrlMap.put(ActorOperations.REMOVE_BATCH.getValue(), new AuditOperation(JsonKey.BATCH, JsonKey.UPDATE));
      auditLogUrlMap.put(ActorOperations.ADD_USER_TO_BATCH.getValue(), new AuditOperation(JsonKey.BATCH, JsonKey.UPDATE));
      auditLogUrlMap.put(ActorOperations.REMOVE_USER_FROM_BATCH.getValue(), new AuditOperation(JsonKey.BATCH, JsonKey.UPDATE));
      auditLogUrlMap.put(ActorOperations.CREATE_NOTE.getValue(), new AuditOperation(JsonKey.USER, JsonKey.CREATE));
      auditLogUrlMap.put(ActorOperations.UPDATE_NOTE.getValue(), new AuditOperation(JsonKey.USER, JsonKey.UPDATE));
      auditLogUrlMap.put(ActorOperations.DELETE_NOTE.getValue(), new AuditOperation(JsonKey.USER, JsonKey.UPDATE));
      
    }

    /**
     * This method will initialize the cassandra data base
     * property 
     */
    private static void initializeDBProperty() {
      //setting db info (keyspace , table) into static map
      //this map will be used during cassandra data base interaction.
      //this map will have each DB name and it's corresponding keyspace and table name.
      dbInfoMap.put(JsonKey.LEARNER_COURSE_DB, getDbInfoObject(KEY_SPACE_NAME,"user_courses"));
      dbInfoMap.put(JsonKey.LEARNER_CONTENT_DB, getDbInfoObject(KEY_SPACE_NAME,"content_consumption"));
      dbInfoMap.put(JsonKey.COURSE_MANAGEMENT_DB, getDbInfoObject(KEY_SPACE_NAME,"course_management"));
      dbInfoMap.put(JsonKey.USER_DB, getDbInfoObject(KEY_SPACE_NAME,"user"));
      dbInfoMap.put(JsonKey.USER_AUTH_DB, getDbInfoObject(KEY_SPACE_NAME,"user_auth"));
      dbInfoMap.put(JsonKey.ORG_DB, getDbInfoObject(KEY_SPACE_NAME,"organisation"));
      dbInfoMap.put(JsonKey.PAGE_MGMT_DB, getDbInfoObject(KEY_SPACE_NAME,"page_management"));
      dbInfoMap.put(JsonKey.PAGE_SECTION_DB, getDbInfoObject(KEY_SPACE_NAME,"page_section"));
      dbInfoMap.put(JsonKey.SECTION_MGMT_DB, getDbInfoObject(KEY_SPACE_NAME,"page_section"));
      dbInfoMap.put(JsonKey.ASSESSMENT_EVAL_DB, getDbInfoObject(KEY_SPACE_NAME,"assessment_eval"));
      dbInfoMap.put(JsonKey.ASSESSMENT_ITEM_DB, getDbInfoObject(KEY_SPACE_NAME,"assessment_item"));
      dbInfoMap.put(JsonKey.ADDRESS_DB, getDbInfoObject(KEY_SPACE_NAME,"address"));
      dbInfoMap.put(JsonKey.EDUCATION_DB, getDbInfoObject(KEY_SPACE_NAME,"user_education"));
      dbInfoMap.put(JsonKey.JOB_PROFILE_DB, getDbInfoObject(KEY_SPACE_NAME,"user_job_profile"));
      dbInfoMap.put(JsonKey.USR_ORG_DB, getDbInfoObject(KEY_SPACE_NAME,"user_org"));
      dbInfoMap.put(JsonKey.USR_EXT_ID_DB, getDbInfoObject(KEY_SPACE_NAME,"user_external_identity"));

      dbInfoMap.put(JsonKey.ORG_MAP_DB, getDbInfoObject(KEY_SPACE_NAME,"org_mapping"));
      dbInfoMap.put(JsonKey.ORG_TYPE_DB, getDbInfoObject(KEY_SPACE_NAME,"org_type"));
      dbInfoMap.put(JsonKey.ROLE, getDbInfoObject(KEY_SPACE_NAME,"role"));
      dbInfoMap.put(JsonKey.MASTER_ACTION, getDbInfoObject(KEY_SPACE_NAME,"master_action"));
      dbInfoMap.put(JsonKey.URL_ACTION, getDbInfoObject(KEY_SPACE_NAME,"url_action"));
      dbInfoMap.put(JsonKey.ACTION_GROUP, getDbInfoObject(KEY_SPACE_NAME,"action_group"));
      dbInfoMap.put(JsonKey.USER_ACTION_ROLE, getDbInfoObject(KEY_SPACE_NAME,"user_action_role"));
      dbInfoMap.put(JsonKey.ROLE_GROUP, getDbInfoObject(KEY_SPACE_NAME,"role_group"));
      dbInfoMap.put(JsonKey.USER_ORG_DB , getDbInfoObject(KEY_SPACE_NAME , "user_org"));
      dbInfoMap.put(JsonKey.BULK_OP_DB , getDbInfoObject(KEY_SPACE_NAME , "bulk_upload_process"));
      dbInfoMap.put(JsonKey.COURSE_BATCH_DB, getDbInfoObject(KEY_SPACE_NAME , "course_batch"));
      dbInfoMap.put(JsonKey.COURSE_PUBLISHED_STATUS, getDbInfoObject(KEY_SPACE_NAME , "course_publish_status"));
      dbInfoMap.put(JsonKey.REPORT_TRACKING_DB ,getDbInfoObject(KEY_SPACE_NAME , "report_tracking"));
      dbInfoMap.put(JsonKey.BADGES_DB, getDbInfoObject(KEY_SPACE_NAME , "badge"));
      dbInfoMap.put(JsonKey.USER_BADGES_DB, getDbInfoObject(KEY_SPACE_NAME , "user_badge"));
      dbInfoMap.put(JsonKey.USER_NOTES_DB, getDbInfoObject(KEY_SPACE_NAME, "user_notes"));
      dbInfoMap.put(JsonKey.MEDIA_TYPE_DB, getDbInfoObject(KEY_SPACE_NAME, "media_type"));
      dbInfoMap.put(JsonKey.USER_SKILL_DB , getDbInfoObject(KEY_SPACE_NAME, "user_skills"));
      dbInfoMap.put(JsonKey.SKILLS_LIST_DB , getDbInfoObject(KEY_SPACE_NAME, "skills"));
      dbInfoMap.put(JsonKey.TENANT_PREFERENCE_DB , getDbInfoObject(KEY_SPACE_NAME, "tenant_preference"));
      dbInfoMap.put(JsonKey.GEO_LOCATION_DB , getDbInfoObject(KEY_SPACE_NAME, "geo_location"));

      dbInfoMap.put(JsonKey.CLIENT_INFO_DB, getDbInfoObject(KEY_SPACE_NAME, "client_info"));
      dbInfoMap.put(JsonKey.SYSTEM_SETTINGS_DB, getDbInfoObject(KEY_SPACE_NAME, "system_settings"));
    }
    
    
    /**
     * This method will take org current state and next state and check 
     * is it possible to move organization from current state to next state 
     * if possible to move then return true else false.
     * @param currentState String
     * @param nextState String
     * @return boolean
     */
    @SuppressWarnings("rawtypes")
    public static boolean checkOrgStatusTransition(Integer currentState , Integer nextState){
        List list = (List)orgStatusTransition.get(currentState);
        if(null == list){
            return false;
        }
        return list.contains(nextState);
    }
    
    /**
     * This method will check the cassandra data base connection.
     * first it will try to established the data base connection from 
     * provided environment variable , if environment variable values 
     * are not set then connection will be established from property file.
     */
    public static void checkCassandraDbConnections(String keySpace) {

      String cassandraMode = ConfigUtil.getString(JsonKey.SUNBIRD_CASSANDRA_MODE);
      if (ProjectUtil.isStringNullOREmpty(cassandraMode) || cassandraMode
          .equalsIgnoreCase(JsonKey.EMBEDDED_MODE)) {

          // configure the Embedded mode and return true here ....
          CassandraConnectionManager cassandraConnectionManager = CassandraConnectionMngrFactory
              .getObject(cassandraMode);
          boolean result = cassandraConnectionManager.createConnection(null , null , null ,null, keySpace);
        if (result) {
          ProjectLogger
              .log("CONNECTION CREATED SUCCESSFULLY FOR IP:" + " : KEYSPACE :"
                      + keySpace,
                  LoggerEnum.INFO.name());
        } else {
          ProjectLogger
              .log("CONNECTION CREATION FAILED FOR IP: " +  " : KEYSPACE :" + keySpace);
        }

        } else if (cassandraMode.equalsIgnoreCase(JsonKey.STANDALONE_MODE)) {
        CassandraConnectionManager cassandraConnectionManager = CassandraConnectionMngrFactory
              .getObject(JsonKey.STANDALONE_MODE);
          List<String> ipList = ConfigUtil.getStringList(JsonKey.SUNBIRD_CASSANDRA_IP);
          List<String> portList = ConfigUtil.getStringList(JsonKey.SUNBIRD_CASSANDRA_PORT);
          //List<String> keyspaceList = ConfigUtil.getStringList(JsonKey.DB_KEYSPACE);

          String userName = prop.getProperty(JsonKey.SUNBIRD_CASSANDRA_USER_NAME);
          String password = prop.getProperty(JsonKey.SUNBIRD_CASSANDRA_PASSWORD);
          for (int i = 0; i < ipList.size(); i++) {
            String ip = ipList.get(i);
            String port = portList.get(i);
            //Reading the same keyspace which is passed in the method
            //String keyspace = keyspaceList[i];

            try {

              boolean result = cassandraConnectionManager.createConnection(ip, port, userName,
                  password, keySpace);
              if (result) {
                ProjectLogger
                    .log("CONNECTION CREATED SUCCESSFULLY FOR IP: " + ip + " : KEYSPACE :"
                            + keySpace,
                        LoggerEnum.INFO.name());
              } else {
                ProjectLogger
                    .log("CONNECTION CREATION FAILED FOR IP: " + ip + " : KEYSPACE :" + keySpace);
              }

            } catch (ProjectCommonException ex) {
              ProjectLogger.log(ex.getMessage(), ex);
            }

          }
        }


    }
    
    public static String getProperty(String key){
          return prop.getProperty(key);
       }


    private static DbInfo getDbInfoObject(String keySpace, String table) {

        DbInfo dbInfo = new DbInfo();

        dbInfo.setKeySpace(keySpace);
        dbInfo.setTableName(table);

        return dbInfo;
    }

    /**
     * class to hold cassandra db info.
     */
    public static class DbInfo {
        String keySpace;
        String tableName;
        String userName;
        String password;
        String ip;
        String port;

        /**
         * @param keySpace
         * @param tableName
         * @param userName
         * @param password
         */
        DbInfo(String keySpace, String tableName, String userName, String password, String ip, String port) {
            this.keySpace = keySpace;
            this.tableName = tableName;
            this.userName = userName;
            this.password = password;
            this.ip = ip;
            this.port = port;
        }

        /**
         * No-arg constructor
         */
        DbInfo() {
        }

        @Override
        public boolean equals(Object obj){
            if(obj instanceof DbInfo){
               DbInfo ob = (DbInfo)obj;
               if(this.ip.equals(ob.getIp()) && this.port.equals(ob.getPort()) &&
                       this.keySpace.equals(ob.getKeySpace())){
                   return true;
               }
            }
            return false;
        }

        @Override
        public int hashCode() {
            return 1;
        }

        public String getKeySpace() {
            return keySpace;
        }

        public void setKeySpace(String keySpace) {
            this.keySpace = keySpace;
        }

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public String getPort() {
            return port;
        }

        public void setPort(String port) {
            this.port = port;
        }
    }
    
    /**
     * This method will take the  map<String,Object> and list<Stirng> of keys.
     * it will remove all the keys from the source map.
     * @param map  Map<String, Object>
     * @param keys List<String> 
     */
    public static void removeAttributes(Map<String, Object> map ,List<String> keys ){

        if(null != map && null != keys) {
            for (String key : keys) {
                map.remove(key);
            }
        }
    }
    
    /**
     * This method will take searchQuery map and internally it will convert map to
     * SearchDto object.
     * @param searchQueryMap Map<String , Object>
     * @return SearchDTO
     */
    @SuppressWarnings("unchecked")
    public static SearchDTO createSearchDto(Map<String , Object> searchQueryMap){
        SearchDTO search = new SearchDTO();
        if(searchQueryMap.containsKey(JsonKey.QUERY)){
         search.setQuery((String) searchQueryMap.get(JsonKey.QUERY));
        }
        if(searchQueryMap.containsKey(JsonKey.FACETS)){
            search.setFacets((List<Map<String , String>>) searchQueryMap.get(JsonKey.FACETS));
        }
        if(searchQueryMap.containsKey(JsonKey.FIELDS)){
            search.setFields((List<String>) searchQueryMap.get(JsonKey.FIELDS));
        }
        if(searchQueryMap.containsKey(JsonKey.FILTERS)){
            search.getAdditionalProperties().put(JsonKey.FILTERS,searchQueryMap.get(JsonKey.FILTERS));
        }
        if(searchQueryMap.containsKey(JsonKey.EXISTS)){
            search.getAdditionalProperties().put(JsonKey.EXISTS, searchQueryMap.get(JsonKey.EXISTS));
        }
        if(searchQueryMap.containsKey(JsonKey.NOT_EXISTS)){
            search.getAdditionalProperties().put(JsonKey.NOT_EXISTS, searchQueryMap.get(JsonKey.NOT_EXISTS));
        }
        if(searchQueryMap.containsKey(JsonKey.SORT_BY)){
            search.getSortBy().putAll((Map<? extends String, ? extends String>) searchQueryMap.get(JsonKey.SORT_BY));
        }
        if(searchQueryMap.containsKey(JsonKey.OFFSET)){
            if((searchQueryMap.get(JsonKey.OFFSET)) instanceof Integer ){
                search.setOffset((int)searchQueryMap.get(JsonKey.OFFSET));
            }else{
                search.setOffset(((BigInteger) searchQueryMap.get(JsonKey.OFFSET)).intValue());
            }
        }
        if(searchQueryMap.containsKey(JsonKey.LIMIT)){
            if((searchQueryMap.get(JsonKey.LIMIT)) instanceof Integer ){
                search.setLimit((int)searchQueryMap.get(JsonKey.LIMIT));
            }else{
                search.setLimit(((BigInteger) searchQueryMap.get(JsonKey.LIMIT)).intValue());
            }
        }
        if(searchQueryMap.containsKey(JsonKey.GROUP_QUERY)){
            search.getGroupQuery().addAll((Collection<? extends Map<String, Object>>) searchQueryMap.get(JsonKey.GROUP_QUERY));
        }
        return search;
    }
    
    /**
     * This method will make a call to EKStep content search api
     * and final response will be appended with same requested map, 
     * with key "contents". Requester can read this key to collect 
     * the response.
     * @param section String, Object> 
     */
    public static void getContentData(Map<String, Object> section) {
        String response = "";
        JSONObject data;
        JSONObject jObject;
        ObjectMapper mapper = new ObjectMapper();
        try {
          String baseSearchUrl = ConfigUtil.getString(JsonKey.EKSTEP_BASE_URL);
          headers.put(JsonKey.AUTHORIZATION, JsonKey.BEARER + ConfigUtil.getString(JsonKey.AUTHORIZATION));
          response = HttpUtil.sendPostRequest(baseSearchUrl + ConfigUtil.getString(JsonKey.EKSTEP_CONTENT_SEARCH_URL),
                    (String) section.get(JsonKey.SEARCH_QUERY), headers);
            jObject = new JSONObject(response);
            data = jObject.getJSONObject(JsonKey.RESULT);
            JSONArray contentArray = data.getJSONArray(JsonKey.CONTENT);
            section.put(JsonKey.CONTENTS, mapper.readValue(contentArray.toString(), Object[].class));
        } catch (IOException | JSONException e) {
            ProjectLogger.log(e.getMessage(), e);
        } 
    }
    
    /**
     * if Object is null then it will return true else false.
     * @param obj Object
     * @return boolean
     */
    public static boolean isNull(Object obj){
        return null == obj ? true:false;
    }
    
    /** 
     * if Object is not null then it will return true else false.
     * @param obj Object
     * @return boolean
     */
    public static boolean isNotNull(Object obj){
        return null != obj? true:false;
    }

    /**
     * This method will provide user name based on user id if user not found
     * then it will return null.
     *
     * @param userId String
     * @return String
     */
    @SuppressWarnings("unchecked")
    public static String getUserNamebyUserId(String userId) {
        CassandraOperation cassandraOperation = ServiceFactory.getInstance();
        Util.DbInfo userdbInfo = Util.dbInfoMap.get(JsonKey.USER_DB);
        Response result = cassandraOperation.getRecordById(userdbInfo.getKeySpace(), userdbInfo.getTableName(), userId);
        List<Map<String, Object>> list = (List<Map<String, Object>>) result.get(JsonKey.RESPONSE);
        if (!(list.isEmpty())) {
            return (String) (list.get(0).get(JsonKey.USERNAME));
        }
        return null;
    }
    
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static String getRootOrgIdFromChannel(String channel) {
      if (!ProjectUtil.isStringNullOREmpty(channel)) {
        Map<String, Object> filters = new HashMap<>();
        filters.put(JsonKey.CHANNEL, channel);
        filters.put(JsonKey.IS_ROOT_ORG, true);
        Map<String, Object> esResult = elasticSearchComplexSearch(filters,
            EsIndex.sunbird.getIndexName(), EsType.organisation.getTypeName());
        if (isNotNull(esResult) && esResult.containsKey(JsonKey.CONTENT)
            && isNotNull(esResult.get(JsonKey.CONTENT))
            && ((List) esResult.get(JsonKey.CONTENT)).size() > 0) {
          Map<String, Object> esContent =
              ((List<Map<String, Object>>) esResult.get(JsonKey.CONTENT)).get(0);
          return (String) esContent.getOrDefault(JsonKey.ID, "");
        }
      }
      return "";
    }
    
    private static Map<String , Object> elasticSearchComplexSearch(Map<String , Object> filters , String index , String type) {

      SearchDTO searchDTO = new SearchDTO();
      searchDTO.getAdditionalProperties().put(JsonKey.FILTERS , filters);

      return ElasticSearchUtil.complexSearch(searchDTO , index,type);

    }
    
    public static String validateRoles(List<String> roleList){
      Map<String,Object> roleMap = DataCacheHandler.getRoleMap();
      if(null != roleMap && !roleMap.isEmpty()){
        for (String role : roleList){
          if(null == roleMap.get(role)){
            return role+" is not a valid role.";
          }
        }
      }else{
        ProjectLogger.log("Roles are not cached.Please Cache it.");
      }
      return JsonKey.SUCCESS;
    }

  public static void main(String[] args) {
    System.out.println("MAIN STARTED");
    checkCassandraDbConnections(JsonKey.SUNBIRD);
    checkCassandraDbConnections(JsonKey.SUNBIRD_PLUGIN);
    System.out.println("MAIN END");
  }
    
    
}
