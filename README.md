# Compress Artifacts

Keeps build artifacts compressed to save disk space on the controller.

Adds an option to compress build artifacts (currently in a ZIP file only) when stored on the controller.
To use, you must request this option in the Jenkins global configuration screen (*Artifact Management for Builds* section).

Artifacts produced before the plugin was installed/configured will not be compressed, though they will be served correctly.

### Compatibility issues

Some other plugins do not yet support nonstandard artifact storage.
In particular, Copy Artifact will be broken. ([JENKINS-22637](https://issues.jenkins-ci.org/browse/JENKINS-22637))
