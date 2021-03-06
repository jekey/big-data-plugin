/*******************************************************************************
 *
 * Pentaho Big Data
 *
 * Copyright (C) 2002-2012 by Pentaho : http://www.pentaho.com
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package org.pentaho.di.job.entries.hadooptransjobexecutor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.vfs.FileObject;
import org.apache.commons.vfs.VFS;
import org.apache.commons.vfs.impl.DefaultFileSystemManager;
import org.junit.BeforeClass;
import org.junit.Test;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.KettleEnvironment;
import org.pentaho.di.core.Result;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.plugins.Plugin;
import org.pentaho.di.core.plugins.PluginInterface;
import org.pentaho.di.core.plugins.PluginMainClassType;
import org.pentaho.di.core.plugins.PluginRegistry;
import org.pentaho.di.core.plugins.StepPluginType;
import org.pentaho.di.core.vfs.KettleVFS;
import org.pentaho.di.job.Job;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.steps.hadoopenter.HadoopEnterMeta;
import org.pentaho.di.trans.steps.hadoopexit.HadoopExitMeta;
import org.pentaho.hadoop.shim.ConfigurationException;
import org.pentaho.hadoop.shim.HadoopConfiguration;
import org.pentaho.hadoop.shim.HadoopConfigurationFileSystemManager;
import org.pentaho.hadoop.shim.MockHadoopConfigurationProvider;
import org.pentaho.hadoop.shim.api.Configuration;
import org.pentaho.hadoop.shim.common.CommonHadoopShim;
import org.pentaho.hadoop.shim.common.ConfigurationProxy;
import org.pentaho.hadoop.shim.spi.HadoopShim;

// TODO Refactor JobEntryHadoopTransJobExecutor so it can be tested better than this pseudo-integration test
public class JobEntryHadoopTransJobExecutorTest {

  @BeforeClass
  public static final void setup() throws Exception {
    KettleEnvironment.init();

    // Register Map/Reduce Input and Map/Reduce Output plugin steps
    PluginMainClassType mainClassTypesAnnotation = StepPluginType.class.getAnnotation(PluginMainClassType.class);

    Map<Class<?>, String> inputClassMap = new HashMap<Class<?>, String>();
    inputClassMap.put(mainClassTypesAnnotation.value(), HadoopEnterMeta.class.getName());
    PluginInterface inputStepPlugin = new Plugin(new String[]{"HadoopEnterPlugin"}, StepPluginType.class, mainClassTypesAnnotation.value(), "Hadoop", "MapReduce Input", "Enter a Hadoop Mapper or Reducer transformation", "MRI.png", false, false, inputClassMap, new ArrayList<String>(), null, null);
    PluginRegistry.getInstance().registerPlugin(StepPluginType.class, inputStepPlugin);

    Map<Class<?>, String> outputClassMap = new HashMap<Class<?>, String>();
    outputClassMap.put(mainClassTypesAnnotation.value(), HadoopExitMeta.class.getName());
    PluginInterface outputStepPlugin = new Plugin(new String[]{"HadoopExitPlugin"}, StepPluginType.class, mainClassTypesAnnotation.value(), "Hadoop", "MapReduce Output", "Exit a Hadoop Mapper or Reducer transformation", "MRO.png", false, false, outputClassMap, new ArrayList<String>(), null, null);
    PluginRegistry.getInstance().registerPlugin(StepPluginType.class, outputStepPlugin);

  }

  // TODO Remove throws Throwable when those contructors are fixed!
  @Test
  public void invalidMapperStepNames() throws Throwable {
    Job job = new Job();
    JobEntryHadoopTransJobExecutor executor = new JobEntryHadoopTransJobExecutor() {
      protected HadoopConfiguration getHadoopConfiguration() throws ConfigurationException {
        try {
          return new HadoopConfiguration(VFS.getManager().resolveFile("ram:///"), "test", "test", new CommonHadoopShim());
        } catch (Exception ex) {
          throw new ConfigurationException("Error creating mock hadoop configuration", ex);
        }
      };
    };
    executor.setParentJob(job);
    executor.setHadoopJobName("hadoop job name");

    executor.setMapTrans("test-res/mr-passthrough.ktr");

    Result result = new Result();

    // No input step name should fail
    executor.execute(result, 0);
    assertEquals(1, result.getNrErrors());

    // Invalid input step name should fail
    result.clear();
    executor.setMapInputStepName("Testing");
    executor.execute(result, 0);
    assertEquals(1, result.getNrErrors());

    // No output step name should fail
    result.clear();
    executor.setMapInputStepName("Injector");
    executor.execute(result, 0);
    assertEquals(1, result.getNrErrors());

    // Invalid output step name should fail
    result.clear();
    executor.setMapInputStepName("Injector");
    executor.setMapOutputStepName("Testing");
    executor.execute(result, 0);
    assertEquals(1, result.getNrErrors());
  }

  @Test
  public void getProperty() throws Throwable {
    JobEntryHadoopTransJobExecutor executor = new JobEntryHadoopTransJobExecutor();
    Configuration conf = new ConfigurationProxy();
    Properties p = new Properties();

    String propertyName = "property";
    String value = "value";
    p.setProperty(propertyName, value);

    assertEquals(value, executor.getProperty(conf, p, propertyName, null));
  }

  @Test
  public void getProperty_overridden() throws Throwable {
    JobEntryHadoopTransJobExecutor executor = new JobEntryHadoopTransJobExecutor();
    Configuration conf = new ConfigurationProxy();
    Properties p = new Properties();

    String propertyName = "property";
    String value = "custom";
    conf.set(propertyName, value);

    assertEquals(value, executor.getProperty(conf, p, propertyName, null));
  }
  
  @Test
  public void getProperty_default() throws Throwable {
    JobEntryHadoopTransJobExecutor executor = new JobEntryHadoopTransJobExecutor();
    Configuration conf = new ConfigurationProxy();
    Properties p = new Properties();

    String propertyName = "property";
    String value = "default-value";

    assertEquals(value, executor.getProperty(conf, p, propertyName, value));
  }
  
  @Test
  public void findAdditionalPluginFolders() throws Throwable {
    JobEntryHadoopTransJobExecutor executor = new JobEntryHadoopTransJobExecutor();
    Configuration conf = new ConfigurationProxy();
    HadoopShim shim = new CommonHadoopShim();
    HadoopConfiguration config = new HadoopConfiguration(VFS.getManager().resolveFile("ram:///"), "test", "test", shim);
    shim.onLoad(config, new HadoopConfigurationFileSystemManager(new MockHadoopConfigurationProvider(), new DefaultFileSystemManager()));
    Properties p = new Properties();
    
    // Fake out the "plugins" directory for the project's root directory
    System.setProperty(Const.PLUGIN_BASE_FOLDERS_PROP, KettleVFS.getFileObject(".").getURL().toURI().getPath());

    p.setProperty(JobEntryHadoopTransJobExecutor.PENTAHO_MAPREDUCE_PROPERTY_ADDITIONAL_PLUGINS, "src, bin   ,  invalid");
    
    List<FileObject> pluginFolders = executor.findAdditionalPluginFolders(shim, conf, p);
    assertEquals(2, pluginFolders.size());
    assertEquals(KettleVFS.getFileObject("./src"), pluginFolders.get(0));
    assertEquals(KettleVFS.getFileObject("./bin"), pluginFolders.get(1));
  }

  @Test
  public void useDistributedCache() throws Throwable {
    JobEntryHadoopTransJobExecutor executor = new JobEntryHadoopTransJobExecutor();

    Configuration conf = new ConfigurationProxy();
    Properties p = new Properties();

    // Default
    assertTrue(executor.useDistributedCache(conf, p));

    // False if set in properties only
    p.setProperty(JobEntryHadoopTransJobExecutor.PENTAHO_MAPREDUCE_PROPERTY_USE_DISTRIBUTED_CACHE, Boolean.toString(false));
    assertFalse(executor.useDistributedCache(conf, p));

    // True if set in properties only
    p.setProperty(JobEntryHadoopTransJobExecutor.PENTAHO_MAPREDUCE_PROPERTY_USE_DISTRIBUTED_CACHE, Boolean.toString(true));
    assertTrue(executor.useDistributedCache(conf, p));

    // False if set in conf, conf overrides properties
    conf.set(JobEntryHadoopTransJobExecutor.PENTAHO_MAPREDUCE_PROPERTY_USE_DISTRIBUTED_CACHE, Boolean.toString(false));
    assertFalse(executor.useDistributedCache(conf, p));

    // True if set in conf, conf overrides properties
    conf.set(JobEntryHadoopTransJobExecutor.PENTAHO_MAPREDUCE_PROPERTY_USE_DISTRIBUTED_CACHE, Boolean.toString(true));
    p.setProperty(JobEntryHadoopTransJobExecutor.PENTAHO_MAPREDUCE_PROPERTY_USE_DISTRIBUTED_CACHE, Boolean.toString(false));
    assertTrue(executor.useDistributedCache(conf, p));
  }

  @Test
  public void verifyTransMetaBadOutputFields() throws IOException, KettleException {
    try {
     TransMeta transMeta = new TransMeta("./test-res/bad-output-fields.ktr");
     
     JobEntryHadoopTransJobExecutor.verifyTransMeta(transMeta, "Injector", "Output");
      fail("Should have thrown an exception");
    } catch (KettleException e) {
      assertTrue("Test for KettleException", e.getMessage().contains("outKey or outValue is not defined in output stream"));
    }
  }

}
