package com.markelliot.gradle.versions;

import com.markelliot.gradle.versions.api.UpdateReport;
import com.markelliot.gradle.versions.api.YamlSerDe;
import com.markelliot.gradle.versions.props.VersionsProps;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;

public abstract class UpdateVersionsPropsTask extends DefaultTask {

    @InputFile
    abstract RegularFileProperty getVersionsProps();

    @InputFiles
    abstract ConfigurableFileCollection getReports();

    @TaskAction
    public final void taskAction() {
        if (!getProject().equals(getProject().getRootProject())) {
            getLogger().warn("Can only run updateVersionsProps on the root project");
            return;
        }

        File versionPropsFile = getVersionsProps().getAsFile().get();
        // collect all the update recommendations.
        List<UpdateReport> reports =
                getReports().getFiles().stream()
                        .map(file -> YamlSerDe.deserialize(file.toPath(), UpdateReport.class))
                        .collect(Collectors.toUnmodifiableList());

        // merge recommendations
        Map<String, String> updateRecs = mergeDependencyUpdates(reports);

        // update versions.props
        VersionsProps versionsProps = VersionsProps.from(versionPropsFile);
        List<VersionsProps.UpdatedLine> updates = new ArrayList<>();
        updateRecs.forEach((k, v) -> versionsProps.update(k, v).ifPresent(updates::add));
        versionsProps.to(versionPropsFile);

        // markdown output
        if (!updates.isEmpty()) {
            Reports.appendMarkdownReport(
                    getProject().getBuildDir(),
                    "## Updated Dependencies\n"
                            + updates.stream()
                                    .filter(u -> !u.oldVersion().equals(u.newVersion()))
                                    .map(
                                            u ->
                                                    String.format(
                                                            " * `%s %s -> %s`\n",
                                                            u.dependency(),
                                                            u.oldVersion(),
                                                            u.newVersion()))
                                    .collect(Collectors.joining()));
        }
    }

    private Map<String, String> mergeDependencyUpdates(List<UpdateReport> reports) {
        Map<String, String> updateRecs = new HashMap<>();
        reports.stream()
                .flatMap(report -> report.dependencyUpdates().stream())
                .forEach(
                        rec -> {
                            String identifier = rec.group() + ":" + rec.name();
                            if (updateRecs.containsKey(identifier)) {
                                if (!updateRecs.get(identifier).equals(rec.latestVersion())) {
                                    getLogger()
                                            .warn(
                                                    "Found conflicting version recommendation for '"
                                                            + identifier
                                                            + "'");

                                    // remove so we ignore the conflict
                                    updateRecs.remove(identifier);
                                }
                            } else {
                                updateRecs.put(identifier, rec.latestVersion());
                            }
                        });
        return updateRecs;
    }
}
