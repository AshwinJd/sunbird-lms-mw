/**
 * 
 */
package org.sunbird.common.quartz.scheduler;

import java.io.InputStream;
import java.util.Properties;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.sunbird.common.models.util.ConfigUtil;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.metrics.actors.MetricsJobScheduler;


/**
 * 
 * This class will manage all the Quartz scheduler. We need to call the schedule method at one time.
 * we are calling this method from Util.java class.
 * 
 * @author Manzarul
 *
 */
public class SchedulerManager {

  private static final String file = "quartz.properties";
  private static Scheduler scheduler = null;
  private static SchedulerManager schedulerManager = null;

  private SchedulerManager() throws CloneNotSupportedException {
    schedule();
  }

  /**
   * This method will register the quartz scheduler job.
   */
  private void schedule() {
    try {
      Thread.sleep(240000);
      boolean isEmbedded = false;
      Properties configProp = null;
      String embeddVal = ConfigUtil.getString(JsonKey.SUNBIRD_QUARTZ_MODE);
      if (JsonKey.EMBEDDED.equalsIgnoreCase(embeddVal)) {
        isEmbedded = true;
      } else {
        configProp = setUpClusterMode();
      }
      if (!isEmbedded && configProp != null) {
        ProjectLogger.log("Quartz scheduler is running in cluster mode.");
        scheduler = new StdSchedulerFactory(configProp).getScheduler();
      } else {
        ProjectLogger.log("Quartz scheduler is running in embedded mode.");
        scheduler = new StdSchedulerFactory().getScheduler();
      }
      String identifier = "NetOps-PC1502295457753";
      // 1- create a job and bind with class which is implementing Job
      // interface.
      JobDetail job = JobBuilder.newJob(ManageCourseBatchCount.class).requestRecovery(true)
          .withIdentity("schedulerJob", identifier).build();

      // 2- Create a trigger object that will define frequency of run.
      // This scheduler will run every day 11:30 PM IN GMT and 6 PM on UTC.
      // server time is set in UTC so all scheduler need to be manage based on that time only.
      Trigger trigger = TriggerBuilder.newTrigger().withIdentity("schedulertrigger", identifier)
          .withSchedule(CronScheduleBuilder
              .cronSchedule(ConfigUtil.getString(JsonKey.COURSE_BATCH_TIMER)))
          .build();
      try {
        if (scheduler.checkExists(job.getKey())) {
          scheduler.deleteJob(job.getKey());
        }
        scheduler.scheduleJob(job, trigger);
        scheduler.start();
        ProjectLogger.log("ManageCourseBatchCount schedular started", LoggerEnum.INFO.name());
      } catch (Exception e) {
        ProjectLogger.log(e.getMessage(), e);
      }
      // add another job for verifying the bulk upload part.
      // 1- create a job and bind with class which is implementing Job
      // interface.
      JobDetail uploadVerifyJob = JobBuilder.newJob(UploadLookUpScheduler.class)
          .requestRecovery(true).withIdentity("uploadVerifyScheduler", identifier).build();

      // 2- Create a trigger object that will define frequency of run.
      // This will run every day 4:30 AM and in UTC 11 PM
      Trigger uploadTrigger =
          TriggerBuilder.newTrigger().withIdentity("uploadVerifyTrigger", identifier)
              .withSchedule(CronScheduleBuilder
                  .cronSchedule(ConfigUtil.getString(JsonKey.UPLOAD_TIMER)))
              .build();
      try {
        if (scheduler.checkExists(uploadVerifyJob.getKey())) {
          scheduler.deleteJob(uploadVerifyJob.getKey());
        }
        scheduler.scheduleJob(uploadVerifyJob, uploadTrigger);
        scheduler.start();
        ProjectLogger.log("UploadLookUpScheduler schedular started", LoggerEnum.INFO.name());
      } catch (Exception e) {
        ProjectLogger.log(e.getMessage(), e);
      }

      // add another job for verifying the course published details from EKStep.
      // 1- create a job and bind with class which is implementing Job
      // interface.
      JobDetail coursePublishedJob = JobBuilder.newJob(CoursePublishedUpdate.class)
          .requestRecovery(true).withIdentity("coursePublishedScheduler", identifier).build();

      // 2- Create a trigger object that will define frequency of run.
      // This job will run every hours.
      Trigger coursePublishedTrigger =
          TriggerBuilder.newTrigger().withIdentity("coursePublishedTrigger", identifier)
              .withSchedule(CronScheduleBuilder.cronSchedule(
                  ConfigUtil.getString(JsonKey.COURSE_PUBLISH_TIMER)))
              .build();
      try {
        if (scheduler.checkExists(coursePublishedJob.getKey())) {
          scheduler.deleteJob(coursePublishedJob.getKey());
        }
        scheduler.scheduleJob(coursePublishedJob, coursePublishedTrigger);
        scheduler.start();
        ProjectLogger.log("CoursePublishedUpdate schedular started", LoggerEnum.INFO.name());
      } catch (Exception e) {
        ProjectLogger.log(e.getMessage(), e);
      }



      // add another job for verifying the course published details from EKStep.
      // 1- create a job and bind with class which is implementing Job
      // interface.
      JobDetail metricsReportJob = JobBuilder.newJob(MetricsReportJob.class).requestRecovery(true)
          .withIdentity("metricsReportJob", identifier).build();

      // 2- Create a trigger object that will define frequency of run.
      // This job will run every day 11:30 PM IN GMT and 6 PM on UTC.
      Trigger metricsReportRetryTrigger =
          TriggerBuilder.newTrigger().withIdentity("metricsReportRetryTrigger", identifier)
              .withSchedule(CronScheduleBuilder.cronSchedule(
                  ConfigUtil.getString(JsonKey.MATRIX_REPORT_TIMER)))
              .build();
      try {
        if (scheduler.checkExists(metricsReportJob.getKey())) {
          scheduler.deleteJob(metricsReportJob.getKey());
        }
        scheduler.scheduleJob(metricsReportJob, metricsReportRetryTrigger);
        scheduler.start();
        ProjectLogger.log("MetricsReportJob schedular started", LoggerEnum.INFO.name());
      } catch (Exception e) {
        ProjectLogger.log(e.getMessage(), e);
        e.printStackTrace();
      }

      // add another job for verifying the course published details from EKStep.
      // 1- create a job and bind with class which is implementing Job
      // interface.
      JobDetail metricsJob = JobBuilder.newJob(MetricsJobScheduler.class).requestRecovery(true)
          .withIdentity("metricsJob", identifier).build();

      // 2- Create a trigger object that will define frequency of run.
      // This job will run every 4 hours everyday.
      Trigger metricsTrigger =
          TriggerBuilder.newTrigger().withIdentity("metricsTrigger", identifier)
              .withSchedule(CronScheduleBuilder
                  .cronSchedule(ConfigUtil.getString(JsonKey.METRICS_TIMER)))
              .build();
      try {
        if (scheduler.checkExists(metricsJob.getKey())) {
          scheduler.deleteJob(metricsJob.getKey());
        }
        scheduler.scheduleJob(metricsJob, metricsTrigger);
        scheduler.start();
        ProjectLogger.log("MetricsJob schedular started", LoggerEnum.INFO.name());
      } catch (Exception e) {
        ProjectLogger.log("Error occured", e);
      }


    } catch (Exception e) {
      ProjectLogger.log("Error in properties cache", e);
      e.printStackTrace();
    } finally {
      registerShutDownHook();
    }
  }

  /**
   * This method will do the Quartz scheduler set up in cluster mode.
   * 
   * @return Properties
   * @throws Exception
   */
  public Properties setUpClusterMode() throws Exception {
    Properties configProp = new Properties();
    String host = ConfigUtil.getString(JsonKey.SUNBIRD_PG_HOST);
    String port = ConfigUtil.getString(JsonKey.SUNBIRD_PG_PORT);
    String db = ConfigUtil.getString(JsonKey.SUNBIRD_PG_DB);
    String username = ConfigUtil.getString(JsonKey.SUNBIRD_PG_USER);
    String password = ConfigUtil.getString(JsonKey.SUNBIRD_PG_PASSWORD);
    ProjectLogger
        .log("Settings for PostGres SQl= host, port,db,username,password " + host
            + " ," + port + "," + db + "," + username + "," + password, LoggerEnum.INFO.name());
    if (!ProjectUtil.isStringNullOREmpty(host) && !ProjectUtil.isStringNullOREmpty(port)
        && !ProjectUtil.isStringNullOREmpty(db) && !ProjectUtil.isStringNullOREmpty(username)
        && !ProjectUtil.isStringNullOREmpty(password)) {
      configProp.put("org.quartz.dataSource.MySqlDS.URL",
          "jdbc:postgresql://" + host + ":" + port + "/" + db);
      configProp.put("org.quartz.dataSource.MySqlDS.user", username);
      configProp.put("org.quartz.dataSource.MySqlDS.password", password);
    } else {
      ProjectLogger.log("Configuration is not set for postgres SQl.",
          LoggerEnum.INFO.name());
      configProp = null;
    }
    return configProp;
  }

  public static SchedulerManager getInstance() {
    if (schedulerManager != null) {
      return schedulerManager;
    }
    try {
      schedulerManager = new SchedulerManager();
    } catch (CloneNotSupportedException e) {
      ProjectLogger.log(e.getMessage(), e);
    }
    return schedulerManager;
  }


  /**
   * This class will be called by registerShutDownHook to register the call inside jvm , when jvm
   * terminate it will call the run method to clean up the resource.
   * 
   * @author Manzarul
   *
   */
  static class ResourceCleanUp extends Thread {
    public void run() {
      ProjectLogger.log("started resource cleanup for Quartz job.");
      try {
        scheduler.shutdown();
      } catch (SchedulerException e) {
        ProjectLogger.log(e.getMessage(), e);
      }
      ProjectLogger.log("completed resource cleanup Quartz job.");
    }
  }

  /**
   * Register the hook for resource clean up. this will be called when jvm shut down.
   */
  public static void registerShutDownHook() {
    Runtime runtime = Runtime.getRuntime();
    runtime.addShutdownHook(new ResourceCleanUp());
    ProjectLogger.log("ShutDownHook registered for Quartz scheduler.");
  }



}
