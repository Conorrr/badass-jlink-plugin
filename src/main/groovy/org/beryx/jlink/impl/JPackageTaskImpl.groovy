/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.beryx.jlink.impl

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.beryx.jlink.data.JPackageTaskData
import org.beryx.jlink.util.Util
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import static org.beryx.jlink.util.Util.EXEC_EXTENSION

@CompileStatic
class JPackageTaskImpl extends BaseTaskImpl<JPackageTaskData> {
    private static final Logger LOGGER = Logging.getLogger(JPackageTaskImpl.class);

    JPackageTaskImpl(Project project, JPackageTaskData taskData) {
        super(project, taskData)
        LOGGER.info("taskData: $taskData")
    }

    void execute() {
        LOGGER.warn("The jpackage task is experimental. Use it at your own risk.")
        jpackageCreateImage()
        if(td.jpackageData.skipInstaller) {
            LOGGER.info("Skipping create-installer")
        } else {
            jpackageCreateInstaller()
        }
    }

    @CompileDynamic
    void jpackageCreateImage() {
        def result = project.exec {
            ignoreExitValue = true
            standardOutput = new ByteArrayOutputStream()
            project.ext.jpackageImageOutput = {
                return standardOutput.toString()
            }
            def outputDir = td.jpackageData.imageOutputDir
            project.delete(outputDir)
            def jpd = td.jpackageData
            def jpackageExec = "$jpd.jpackageHome/bin/jpackage$EXEC_EXTENSION"
            Util.checkExecutable(jpackageExec)
            commandLine = [jpackageExec,
                           'create-image',
                           '--output', outputDir,
                           '--name', jpd.imageName,
                           '--module-path', td.jlinkJarsDir,
                           '--module', "$td.moduleName/$td.mainClass",
                           '--runtime-image', td.runtimeImageDir,
                           *(jpd.jvmArgs ? jpd.jvmArgs.collect{['--java-options', '"'+it+'"']}.flatten() : []),
                           *jpd.imageOptions]
        }
        if(result.exitValue != 0) {
            LOGGER.error(project.ext.jpackageImageOutput())
        } else {
            LOGGER.info(project.ext.jpackageImageOutput())
        }
        result.assertNormalExitValue()
        result.rethrowFailure()
    }

    @CompileDynamic
    void jpackageCreateInstaller() {
        def jpd = td.jpackageData
        def appImagePath = "${td.jpackageData.getImageOutputDir()}/$jpd.imageName"
        if(org.gradle.internal.os.OperatingSystem.current().macOsX) {
            def appImageDir = new File(appImagePath)
            if(!appImageDir.directory) {
                def currImagePath = "${td.jpackageData.getImageOutputDir()}/${jpd.imageName}.app"
                if(!new File(currImagePath).directory) {
                    throw new GradleException("Unable to find the application image in ${td.jpackageData.getImageOutputDir()}")
                }
                appImagePath = currImagePath
            }
        }

        def result = project.exec {
            ignoreExitValue = true
            standardOutput = new ByteArrayOutputStream()
            project.ext.jpackageInstallerOutput = {
                return standardOutput.toString()
            }
            if (td.jpackageData.getImageOutputDir() != td.jpackageData.getInstallerOutputDir())
                FileUtils.cleanDirectory(td.jpackageData.getInstallerOutputDir())
            commandLine = ["$jpd.jpackageHome/bin/jpackage",
                           'create-installer',
                           *(jpd.installerType ? ['--installer-type', jpd.installerType] : []),
                           '--output', td.jpackageData.getInstallerOutputDir(),
                           '--name', jpd.installerName,
                           '--app-image', "$appImagePath",
                           *jpd.installerOptions]
        }
        if(result.exitValue != 0) {
            LOGGER.error(project.ext.jpackageInstallerOutput())
        } else {
            LOGGER.info(project.ext.jpackageInstallerOutput())
        }
        result.assertNormalExitValue()
        result.rethrowFailure()
    }

}
