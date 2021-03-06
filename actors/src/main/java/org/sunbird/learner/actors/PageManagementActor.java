package org.sunbird.learner.actors;

import akka.actor.UntypedAbstractActor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.request.ExecutionContext;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.learner.util.DataCacheHandler;
import org.sunbird.learner.util.EkStepRequestUtil;
import org.sunbird.learner.util.TelemetryUtil;
import org.sunbird.learner.util.Util;

/**
 * This actor will handle page management operation .
 *
 * @author Amit Kumar
 */
public class PageManagementActor extends UntypedAbstractActor {

  private Util.DbInfo pageDbInfo = Util.dbInfoMap.get(JsonKey.PAGE_MGMT_DB);
  private Util.DbInfo sectionDbInfo = Util.dbInfoMap.get(JsonKey.SECTION_MGMT_DB);
  private Util.DbInfo pageSectionDbInfo = Util.dbInfoMap.get(JsonKey.PAGE_SECTION_DB);
  private Util.DbInfo orgDbInfo = Util.dbInfoMap.get(JsonKey.ORG_DB);
  private CassandraOperation cassandraOperation = ServiceFactory.getInstance();

  @Override
  public void onReceive(Object message) throws Throwable {
    if (message instanceof Request) {
      try {
        ProjectLogger.log("PageManagementActor onReceive called");
        Request actorMessage = (Request) message;
        Util.initializeContext(actorMessage, JsonKey.PAGE);
        //set request id fto thread loacl...
        ExecutionContext.setRequestId(actorMessage.getRequestId());
        if (actorMessage.getOperation().equalsIgnoreCase(ActorOperations.CREATE_PAGE.getValue())) {
          createPage(actorMessage);
        } else if (actorMessage.getOperation()
            .equalsIgnoreCase(ActorOperations.UPDATE_PAGE.getValue())) {
          updatePage(actorMessage);
        } else if (actorMessage.getOperation()
            .equalsIgnoreCase(ActorOperations.GET_PAGE_SETTING.getValue())) {
          getPageSetting(actorMessage);
        } else if (actorMessage.getOperation()
            .equalsIgnoreCase(ActorOperations.GET_PAGE_SETTINGS.getValue())) {
          getPageSettings();
        } else if (actorMessage.getOperation()
            .equalsIgnoreCase(ActorOperations.GET_PAGE_DATA.getValue())) {
          getPageData(actorMessage);
        } else if (actorMessage.getOperation()
            .equalsIgnoreCase(ActorOperations.CREATE_SECTION.getValue())) {
          createPageSection(actorMessage);
        } else if (actorMessage.getOperation()
            .equalsIgnoreCase(ActorOperations.UPDATE_SECTION.getValue())) {
          updatePageSection(actorMessage);
        } else if (actorMessage.getOperation()
            .equalsIgnoreCase(ActorOperations.GET_SECTION.getValue())) {
          getSection(actorMessage);
        } else if (actorMessage.getOperation()
            .equalsIgnoreCase(ActorOperations.GET_ALL_SECTION.getValue())) {
          getAllSections();
        } else {
          ProjectLogger.log("UNSUPPORTED OPERATION");
          ProjectCommonException exception =
              new ProjectCommonException(ResponseCode.invalidOperationName.getErrorCode(),
                  ResponseCode.invalidOperationName.getErrorMessage(),
                  ResponseCode.CLIENT_ERROR.getResponseCode());
          sender().tell(exception, self());
        }
      } catch (Exception ex) {
        ProjectLogger.log(ex.getMessage(), ex);
        sender().tell(ex, self());
      }
    } else {
      // Throw exception as message body
      ProjectLogger.log("UNSUPPORTED MESSAGE");
      ProjectCommonException exception =
          new ProjectCommonException(ResponseCode.invalidRequestData.getErrorCode(),
              ResponseCode.invalidRequestData.getErrorMessage(),
              ResponseCode.CLIENT_ERROR.getResponseCode());
      sender().tell(exception, self());
    }

  }

  private void getAllSections() {
    Response response = null;
    response =
        cassandraOperation.getAllRecords(sectionDbInfo.getKeySpace(), sectionDbInfo.getTableName());
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> result =
        (List<Map<String, Object>>) response.getResult().get(JsonKey.RESPONSE);
    for (Map<String, Object> map : result) {
      removeUnwantedData(map, "");
    }
    Response sectionMap = new Response();
    sectionMap.put(JsonKey.SECTIONS, response.get(JsonKey.RESPONSE));
    sender().tell(response, self());

  }

  private void getSection(Request actorMessage) {
    Response response = null;
    Map<String, Object> req = actorMessage.getRequest();
    String sectionId = (String) req.get(JsonKey.ID);
    response = cassandraOperation.getRecordById(sectionDbInfo.getKeySpace(),
        sectionDbInfo.getTableName(), sectionId);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> result =
        (List<Map<String, Object>>) response.getResult().get(JsonKey.RESPONSE);
    if (!(result.isEmpty())) {
      Map<String, Object> map = result.get(0);
      removeUnwantedData(map, "");
      Response section = new Response();
      section.put(JsonKey.SECTION, response.get(JsonKey.RESPONSE));
    }
    sender().tell(response, self());
  }

  private void updatePageSection(Request actorMessage) {
    Map<String, Object> req = actorMessage.getRequest();
    //object of telemetry event...
    Map<String, Object> targetObject = new HashMap<>();
    List<Map<String, Object>> correlatedObject = new ArrayList<>();
    @SuppressWarnings("unchecked")
    Map<String, Object> sectionMap = (Map<String, Object>) req.get(JsonKey.SECTION);
    if (null != sectionMap.get(JsonKey.SEARCH_QUERY)) {
      ObjectMapper mapper = new ObjectMapper();
      try {
        sectionMap.put(JsonKey.SEARCH_QUERY,
            mapper.writeValueAsString(sectionMap.get(JsonKey.SEARCH_QUERY)));
      } catch (IOException e) {
        ProjectLogger.log(e.getMessage(), e);
      }
    }
    if (null != sectionMap.get(JsonKey.SECTION_DISPLAY)) {
      ObjectMapper mapper = new ObjectMapper();
      try {
        sectionMap.put(JsonKey.SECTION_DISPLAY,
            mapper.writeValueAsString(sectionMap.get(JsonKey.SECTION_DISPLAY)));
      } catch (IOException e) {
        ProjectLogger.log(e.getMessage(), e);
      }
    }
    sectionMap.put(JsonKey.UPDATED_DATE, ProjectUtil.getFormattedDate());
    Response response = cassandraOperation.updateRecord(sectionDbInfo.getKeySpace(),
        sectionDbInfo.getTableName(), sectionMap);
    sender().tell(response, self());
    targetObject = TelemetryUtil
        .generateTargetObject((String)sectionMap.get(JsonKey.ID), JsonKey.PAGE_SECTION, JsonKey.CREATE, null);
    TelemetryUtil.telemetryProcessingCall(actorMessage.getRequest(), targetObject, correlatedObject);
    //TelemetryUtil.generateCorrelatedObject(endoresedUserId, JsonKey.USER , null , correlatedObject);

    // update DataCacheHandler section map with updated page section data
    new Thread() {
      @Override
      public void run() {
        if (((String) response.get(JsonKey.RESPONSE)).equalsIgnoreCase(JsonKey.SUCCESS)) {
          DataCacheHandler.getSectionMap().put((String) sectionMap.get(JsonKey.ID), sectionMap);
        }
      }
    }.start();
  }

  private void createPageSection(Request actorMessage) {
    Map<String, Object> req = actorMessage.getRequest();
    @SuppressWarnings("unchecked")
    Map<String, Object> sectionMap = (Map<String, Object>) req.get(JsonKey.SECTION);
    //object of telemetry event...
    Map<String, Object> targetObject = new HashMap<>();
    List<Map<String, Object>> correlatedObject = new ArrayList<>();
    String uniqueId = ProjectUtil.getUniqueIdFromTimestamp(actorMessage.getEnv());
    if (null != sectionMap.get(JsonKey.SEARCH_QUERY)) {
      ObjectMapper mapper = new ObjectMapper();
      try {
        sectionMap.put(JsonKey.SEARCH_QUERY,
            mapper.writeValueAsString(sectionMap.get(JsonKey.SEARCH_QUERY)));
      } catch (IOException e) {
        ProjectLogger.log(e.getMessage(), e);
      }
    }
    if (null != sectionMap.get(JsonKey.SECTION_DISPLAY)) {
      ObjectMapper mapper = new ObjectMapper();
      try {
        sectionMap.put(JsonKey.SECTION_DISPLAY,
            mapper.writeValueAsString(sectionMap.get(JsonKey.SECTION_DISPLAY)));
      } catch (IOException e) {
        ProjectLogger.log(e.getMessage(), e);
      }
    }
    sectionMap.put(JsonKey.ID, uniqueId);
    sectionMap.put(JsonKey.STATUS, ProjectUtil.Status.ACTIVE.getValue());
    sectionMap.put(JsonKey.CREATED_DATE, ProjectUtil.getFormattedDate());
    Response response = cassandraOperation.insertRecord(sectionDbInfo.getKeySpace(),
        sectionDbInfo.getTableName(), sectionMap);
    response.put(JsonKey.SECTION_ID, uniqueId);
    sender().tell(response, self());
    targetObject = TelemetryUtil
        .generateTargetObject(uniqueId, JsonKey.PAGE_SECTION, JsonKey.CREATE, null);
    TelemetryUtil.telemetryProcessingCall(actorMessage.getRequest(), targetObject, correlatedObject);
    //TelemetryUtil.generateCorrelatedObject(endoresedUserId, JsonKey.USER , null , correlatedObject);

    // update DataCacheHandler section map with new page section data
    new Thread() {
      @Override
      public void run() {
        if (((String) response.get(JsonKey.RESPONSE)).equalsIgnoreCase(JsonKey.SUCCESS)) {
          DataCacheHandler.getSectionMap().put((String) sectionMap.get(JsonKey.ID), sectionMap);
        }
      }
    }.start();
  }

  @SuppressWarnings("unchecked")
  private void getPageData(Request actorMessage) {
    String sectionQuery = null;
    List<Map<String, Object>> sectionList = new ArrayList<>();
    Map<String, Object> filterMap = new HashMap<>();
    Response response = null;
    Map<String, Object> req = (Map<String, Object>) actorMessage.getRequest().get(JsonKey.PAGE);
    String pageName = (String) req.get(JsonKey.PAGE_NAME);
    String source = (String) req.get(JsonKey.SOURCE);
    String orgCode = (String) req.get(JsonKey.ORG_CODE);
    Map<String, String> headers =
        (Map<String, String>) actorMessage.getRequest().get(JsonKey.HEADER);
    filterMap.putAll(req);
    filterMap.remove(JsonKey.PAGE_NAME);
    filterMap.remove(JsonKey.SOURCE);
    filterMap.remove(JsonKey.ORG_CODE);
    filterMap.remove(JsonKey.FILTERS);
    filterMap.remove(JsonKey.CREATED_BY);
    Map<String, Object> reqFilters = (Map<String, Object>) req.get(JsonKey.FILTERS);
    List<Map<String, Object>> result = null;
    try {
      if (!ProjectUtil.isStringNullOREmpty(orgCode)) {
        response = cassandraOperation.getRecordsByProperty(orgDbInfo.getKeySpace(),
            orgDbInfo.getTableName(), JsonKey.ORG_CODE, orgCode);
        result = (List<Map<String, Object>>) response.getResult().get(JsonKey.RESPONSE);
      }
    } catch (Exception e) {
      ProjectLogger.log(e.getMessage(), e);
    }

    Map<String, Object> map = null;
    String orgId = "NA";
    if (null != result && !result.isEmpty()) {
      map = result.get(0);
      orgId = (String) map.get(JsonKey.ID);
    }

    Map<String, Object> pageMap = DataCacheHandler.getPageMap().get(orgId + ":" + pageName);
    /**
     * if requested page for this organization is not found, return default NTP page
     */
    if (null == pageMap) {
      pageMap = DataCacheHandler.getPageMap().get("NA" + ":" + pageName);
    }
    if (source.equalsIgnoreCase(ProjectUtil.Source.WEB.getValue())) {
      if (null != pageMap && null != pageMap.get(JsonKey.PORTAL_MAP)) {
        sectionQuery = (String) pageMap.get(JsonKey.PORTAL_MAP);
      }
    } else {
      if (null != pageMap && null != pageMap.get(JsonKey.APP_MAP)) {
        sectionQuery = (String) pageMap.get(JsonKey.APP_MAP);
      }
    }
    ObjectMapper mapper = new ObjectMapper();
    Map<String, Object> responseMap = new HashMap<>();

    try {
      Object[] arr = mapper.readValue(sectionQuery, Object[].class);

      for (Object obj : arr) {
        Map<String, Object> sectionMap = (Map<String, Object>) obj;
        Map<String, Object> sectionData = new HashMap<>(
            DataCacheHandler.getSectionMap().get((String) sectionMap.get(JsonKey.ID)));
        getContentData(sectionData, reqFilters, headers, filterMap);
        sectionData.put(JsonKey.GROUP, sectionMap.get(JsonKey.GROUP));
        sectionData.put(JsonKey.INDEX, sectionMap.get(JsonKey.INDEX));
        removeUnwantedData(sectionData, "getPageData");
        sectionList.add(sectionData);
      }

      responseMap.put(JsonKey.NAME, pageMap.get(JsonKey.NAME));
      responseMap.put(JsonKey.ID, pageMap.get(JsonKey.ID));
      responseMap.put(JsonKey.SECTIONS, sectionList);
    } catch (IOException e) {
      ProjectLogger.log(e.getMessage(), e);
    }
    Response pageResponse = new Response();
    pageResponse.put(JsonKey.RESPONSE, responseMap);
    sender().tell(pageResponse, self());
  }

  @SuppressWarnings("unchecked")
  private void getPageSetting(Request actorMessage) {
    Map<String, Object> req = actorMessage.getRequest();
    String pageName = (String) req.get(JsonKey.ID);
    Response response = cassandraOperation.getRecordsByProperty(pageDbInfo.getKeySpace(),
        pageDbInfo.getTableName(), JsonKey.PAGE_NAME, pageName);
    List<Map<String, Object>> result =
        (List<Map<String, Object>>) response.getResult().get(JsonKey.RESPONSE);
    if (!(result.isEmpty())) {
      Map<String, Object> pageDO = result.get(0);
      Map<String, Object> responseMap = getPageSetting(pageDO);
      response.getResult().put(JsonKey.PAGE, responseMap);
      response.getResult().remove(JsonKey.RESPONSE);
    }
    sender().tell(response, self());
  }

  @SuppressWarnings("unchecked")
  private void getPageSettings() {
    Response response =
        cassandraOperation.getAllRecords(pageDbInfo.getKeySpace(), pageDbInfo.getTableName());
    List<Map<String, Object>> result =
        (List<Map<String, Object>>) response.getResult().get(JsonKey.RESPONSE);
    List<Map<String, Object>> pageList = new ArrayList<>();
    for (Map<String, Object> pageDO : result) {
      Map<String, Object> responseMap = getPageSetting(pageDO);
      pageList.add(responseMap);
    }
    response.getResult().put(JsonKey.PAGE, pageList);
    response.getResult().remove(JsonKey.RESPONSE);
    sender().tell(response, self());
  }

  @SuppressWarnings("unchecked")
  private void updatePage(Request actorMessage) {
    Map<String, Object> req = actorMessage.getRequest();
    Map<String, Object> pageMap = (Map<String, Object>) req.get(JsonKey.PAGE);
    //object of telemetry event...
    Map<String, Object> targetObject = new HashMap<>();
    List<Map<String, Object>> correlatedObject = new ArrayList<>();
    // default value for orgId
    if (ProjectUtil.isStringNullOREmpty((String) pageMap.get(JsonKey.ORGANISATION_ID))) {
      pageMap.put(JsonKey.ORGANISATION_ID, "NA");
    }
    if (!ProjectUtil.isStringNullOREmpty((String) pageMap.get(JsonKey.PAGE_NAME))) {
      Map<String, Object> map = new HashMap<>();
      map.put(JsonKey.PAGE_NAME, pageMap.get(JsonKey.PAGE_NAME));
      map.put(JsonKey.ORGANISATION_ID, pageMap.get(JsonKey.ORGANISATION_ID));

      Response res = cassandraOperation.getRecordsByProperties(pageDbInfo.getKeySpace(),
          pageDbInfo.getTableName(), map);
      if (!((List<Map<String, Object>>) res.get(JsonKey.RESPONSE)).isEmpty()) {
        Map<String, Object> page = ((List<Map<String, Object>>) res.get(JsonKey.RESPONSE)).get(0);
        if (!(((String) page.get(JsonKey.ID)).equals((String) pageMap.get(JsonKey.ID)))) {
          ProjectCommonException exception =
              new ProjectCommonException(ResponseCode.pageAlreadyExist.getErrorCode(),
                  ResponseCode.pageAlreadyExist.getErrorMessage(),
                  ResponseCode.CLIENT_ERROR.getResponseCode());
          sender().tell(exception, self());
          return;
        }
      }
    }
    pageMap.put(JsonKey.UPDATED_DATE, ProjectUtil.getFormattedDate());
    if (null != pageMap.get(JsonKey.PORTAL_MAP)) {
      ObjectMapper mapper = new ObjectMapper();
      try {
        pageMap.put(JsonKey.PORTAL_MAP, mapper.writeValueAsString(pageMap.get(JsonKey.PORTAL_MAP)));
      } catch (IOException e) {
        ProjectLogger.log(e.getMessage(), e);
      }
    }
    if (null != pageMap.get(JsonKey.APP_MAP)) {
      ObjectMapper mapper = new ObjectMapper();
      try {
        pageMap.put(JsonKey.APP_MAP, mapper.writeValueAsString(pageMap.get(JsonKey.APP_MAP)));
      } catch (IOException e) {
        ProjectLogger.log(e.getMessage(), e);
      }
    }
    Response response = cassandraOperation.updateRecord(pageDbInfo.getKeySpace(),
        pageDbInfo.getTableName(), pageMap);
    sender().tell(response, self());

    targetObject = TelemetryUtil
        .generateTargetObject((String)pageMap.get(JsonKey.ID), JsonKey.PAGE, JsonKey.CREATE, null);
    TelemetryUtil.telemetryProcessingCall(actorMessage.getRequest(), targetObject, correlatedObject);
    //TelemetryUtil.generateCorrelatedObject(endoresedUserId, JsonKey.USER , null , correlatedObject);
    // update DataCacheHandler page map with updated page data
    new Thread() {
      @Override
      public void run() {
        if (((String) response.get(JsonKey.RESPONSE)).equalsIgnoreCase(JsonKey.SUCCESS)) {
          String orgId = "NA";
          if (pageMap.containsKey(JsonKey.ORGANISATION_ID)) {
            orgId = (String) pageMap.get(JsonKey.ORGANISATION_ID);
          }
          DataCacheHandler.getPageMap().put(orgId + ":" + (String) pageMap.get(JsonKey.PAGE_NAME),
              pageMap);
        }
      }
    }.start();
  }

  @SuppressWarnings("unchecked")
  private void createPage(Request actorMessage) {
    Map<String, Object> req = actorMessage.getRequest();
    Map<String, Object> pageMap = (Map<String, Object>) req.get(JsonKey.PAGE);
    //object of telemetry event...
    Map<String, Object> targetObject = new HashMap<>();
    List<Map<String, Object>> correlatedObject = new ArrayList<>();
    // default value for orgId
    if (ProjectUtil.isStringNullOREmpty((String) pageMap.get(JsonKey.ORGANISATION_ID))) {
      pageMap.put(JsonKey.ORGANISATION_ID, "NA");
    }
    String uniqueId = ProjectUtil.getUniqueIdFromTimestamp(actorMessage.getEnv());
    if (!ProjectUtil.isStringNullOREmpty((String) pageMap.get(JsonKey.PAGE_NAME))) {
      Map<String, Object> map = new HashMap<>();
      map.put(JsonKey.PAGE_NAME, pageMap.get(JsonKey.PAGE_NAME));
      map.put(JsonKey.ORGANISATION_ID, pageMap.get(JsonKey.ORGANISATION_ID));

      Response res = cassandraOperation.getRecordsByProperties(pageDbInfo.getKeySpace(),
          pageDbInfo.getTableName(), map);
      if (!((List<Map<String, Object>>) res.get(JsonKey.RESPONSE)).isEmpty()) {
        ProjectCommonException exception =
            new ProjectCommonException(ResponseCode.pageAlreadyExist.getErrorCode(),
                ResponseCode.pageAlreadyExist.getErrorMessage(),
                ResponseCode.CLIENT_ERROR.getResponseCode());
        sender().tell(exception, self());
        return;
      }
    }
    pageMap.put(JsonKey.ID, uniqueId);
    pageMap.put(JsonKey.CREATED_DATE, ProjectUtil.getFormattedDate());
    if (null != pageMap.get(JsonKey.PORTAL_MAP)) {
      ObjectMapper mapper = new ObjectMapper();
      try {
        pageMap.put(JsonKey.PORTAL_MAP, mapper.writeValueAsString(pageMap.get(JsonKey.PORTAL_MAP)));
      } catch (IOException e) {
        ProjectLogger.log(e.getMessage(), e);
      }
    }
    if (null != pageMap.get(JsonKey.APP_MAP)) {
      ObjectMapper mapper = new ObjectMapper();
      try {
        pageMap.put(JsonKey.APP_MAP, mapper.writeValueAsString(pageMap.get(JsonKey.APP_MAP)));
      } catch (IOException e) {
        ProjectLogger.log(e.getMessage(), e);
      }
    }
    Response response = cassandraOperation.insertRecord(pageDbInfo.getKeySpace(),
        pageDbInfo.getTableName(), pageMap);
    response.put(JsonKey.PAGE_ID, uniqueId);
    sender().tell(response, self());
    targetObject = TelemetryUtil
        .generateTargetObject(uniqueId, JsonKey.PAGE, JsonKey.CREATE, null);
    TelemetryUtil.telemetryProcessingCall(actorMessage.getRequest(), targetObject, correlatedObject);

    // update DataCacheHandler page map with new page data
    new Thread() {
      @Override
      public void run() {
        if (((String) response.get(JsonKey.RESPONSE)).equalsIgnoreCase(JsonKey.SUCCESS)) {
          String orgId = "NA";
          if (pageMap.containsKey(JsonKey.ORGANISATION_ID)) {
            orgId = (String) pageMap.get(JsonKey.ORGANISATION_ID);
          }
          DataCacheHandler.getPageMap().put(orgId + ":" + (String) pageMap.get(JsonKey.PAGE_NAME),
              pageMap);
        }
      }
    }.start();
  }

  @SuppressWarnings("unchecked")
  private void getContentData(Map<String, Object> section, Map<String, Object> reqFilters,
      Map<String, String> headers, Map<String, Object> filterMap) {
    ObjectMapper mapper = new ObjectMapper();
    Map<String, Object> map = new HashMap<>();
    try {
      map = mapper.readValue((String) section.get(JsonKey.SEARCH_QUERY), HashMap.class);
    } catch (IOException e) {
      ProjectLogger.log(e.getMessage(), e);
    }
    Set<Entry<String, Object>> filterEntrySet = filterMap.entrySet();
    for (Entry<String, Object> entry : filterEntrySet) {
      if (!entry.getKey().equalsIgnoreCase(JsonKey.FILTERS)) {
        ((Map<String, Object>) map.get(JsonKey.REQUEST)).put(entry.getKey(), entry.getValue());
      }
    }
    Map<String, Object> filters =
        (Map<String, Object>) ((Map<String, Object>) map.get(JsonKey.REQUEST)).get(JsonKey.FILTERS);
    ProjectLogger.log("default search query for ekstep for page data assemble api : "
        + (String) section.get(JsonKey.SEARCH_QUERY));
    applyFilters(filters, reqFilters);
    String query = "";

    try {
      query = mapper.writeValueAsString(map);
    } catch (Exception e) {
      ProjectLogger.log("Exception occurred while parsing filters for Ekstep search query", e);
    }
    if (ProjectUtil.isStringNullOREmpty(query)) {
      query = (String) section.get(JsonKey.SEARCH_QUERY);
    }
    ProjectLogger
        .log("search query after applying filter for ekstep for page data assemble api : " + query);
    Map<String,Object> result = EkStepRequestUtil.searchContent(query, headers);
    if (null != result && !result.isEmpty()) {
      section.put(JsonKey.CONTENTS, result.get(JsonKey.CONTENTS));
      Map<String,Object> tempMap = (Map<String, Object>) result.get(JsonKey.PARAMS);
      section.put(JsonKey.RES_MSG_ID, tempMap.get(JsonKey.RES_MSG_ID));
      section.put(JsonKey.API_ID, tempMap.get(JsonKey.API_ID));
    }
  }

  @SuppressWarnings("unchecked")
  /**
   * combine both requested page filters with default page filters.
   *
   * @param filters
   * @param reqFilters
   */
  private void applyFilters(Map<String, Object> filters, Map<String, Object> reqFilters) {
    if (null != reqFilters) {
      Set<Entry<String, Object>> entrySet = reqFilters.entrySet();
      for (Entry<String, Object> entry : entrySet) {
        String key = entry.getKey();
        if (filters.containsKey(key)) {
          Object obj = entry.getValue();
          if (obj instanceof List) {
            if (filters.get(key) instanceof List) {
              Set<Object> set = new HashSet<>((List<Object>) filters.get(key));
              set.addAll((List<Object>) obj);
              ((List<Object>) filters.get(key)).clear();
              ((List<Object>) filters.get(key)).addAll(set);
            } else if (filters.get(key) instanceof Map) {
              filters.put(key, obj);
            } else {
              if (!(((List<Object>) obj).contains((String) filters.get(key)))) {
                ((List<Object>) obj).add((String) filters.get(key));
              }
              filters.put(key, obj);
            }
          } else if (obj instanceof Map) {
            filters.put(key, obj);
          } else {
            if (filters.get(key) instanceof List) {
              if (!(((List<Object>) filters.get(key)).contains(obj))) {
                ((List<Object>) filters.get(key)).add(obj);
              }
            } else if (filters.get(key) instanceof Map) {
              filters.put(key, obj);
            } else {
              List<Object> list = new ArrayList<>();
              list.add(filters.get(key));
              list.add(obj);
              filters.put(key, list);
            }
          }
        } else {
          filters.put(key, entry.getValue());
        }
      }
    }
  }

  private Map<String, Object> getPageSetting(Map<String, Object> pageDO) {

    Map<String, Object> responseMap = new HashMap<>();
    responseMap.put(JsonKey.NAME, pageDO.get(JsonKey.NAME));
    responseMap.put(JsonKey.ID, pageDO.get(JsonKey.ID));

    if (pageDO.containsKey(JsonKey.APP_MAP) && null != pageDO.get(JsonKey.APP_MAP)) {
      responseMap.put(JsonKey.APP_SECTIONS, parsePage(pageDO, (String) JsonKey.APP_MAP));
    }
    if (pageDO.containsKey(JsonKey.PORTAL_MAP) && null != pageDO.get(JsonKey.PORTAL_MAP)) {
      responseMap.put(JsonKey.PORTAL_SECTIONS, parsePage(pageDO, (String) JsonKey.PORTAL_MAP));
    }
    return responseMap;
  }

  private void removeUnwantedData(Map<String, Object> map, String from) {
    map.remove(JsonKey.CREATED_DATE);
    map.remove(JsonKey.CREATED_BY);
    map.remove(JsonKey.UPDATED_DATE);
    map.remove(JsonKey.UPDATED_BY);
    if (from.equalsIgnoreCase("getPageData")) {
      map.remove(JsonKey.STATUS);
    }
  }


  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> parsePage(Map<String, Object> pageDO, String mapType) {
    List<Map<String, Object>> sections = new ArrayList<>();
    String sectionQuery = (String) pageDO.get(mapType);
    ObjectMapper mapper = new ObjectMapper();
    try {
      Object[] arr = mapper.readValue(sectionQuery, Object[].class);
      for (Object obj : arr) {
        Map<String, Object> sectionMap = (Map<String, Object>) obj;
        Response sectionResponse = cassandraOperation.getRecordById(pageSectionDbInfo.getKeySpace(),
            pageSectionDbInfo.getTableName(), (String) sectionMap.get(JsonKey.ID));

        List<Map<String, Object>> sectionResult =
            (List<Map<String, Object>>) sectionResponse.getResult().get(JsonKey.RESPONSE);
        if (null != sectionResult && !sectionResult.isEmpty()) {
          sectionResult.get(0).put(JsonKey.GROUP, sectionMap.get(JsonKey.GROUP));
          sectionResult.get(0).put(JsonKey.INDEX, sectionMap.get(JsonKey.INDEX));
          removeUnwantedData(sectionResult.get(0), "");
          sections.add(sectionResult.get(0));
        }
      }
    } catch (Exception e) {
      ProjectLogger.log(e.getMessage(), e);
    }
    return sections;
  }

}
