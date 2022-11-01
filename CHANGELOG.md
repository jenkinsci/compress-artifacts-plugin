## Changelog

### Version 1.7

- Optimize artifact archiving. ([JENKINS-28862](https://issues.jenkins-ci.org/browse/JENKINS-28862))
- Fix regression of [JENKINS-27042](https://issues.jenkins-ci.org/browse/JENKINS-27042) from 1.6.

### Version 1.5

- Unable to compress artifacts with non-IBM437 characters in filename. Regression introduced in 1.4. ([JENKINS-27522](https://issues.jenkins-ci.org/browse/JENKINS-27522))

### Version 1.4

- java.util.zip.ZipException thrown when reading artifacts from archive larger than 4G. ([JENKINS-27042](https://issues.jenkins-ci.org/browse/JENKINS-27042))

Stability issues

Before this version, plugin is not able to serve artifacts when archive exceeds 4G in size, it leads to JVM crashes on java 6. ([JENKINS-27042](https://issues.jenkins-ci.org/browse/JENKINS-27042))

### Version 1.3

- Avoid ZipException thrown when accessing artifiacts while archiving. [4720879](https://github.com/jenkinsci/compress-artifacts-plugin/commit/47208791705ed6d77bbc4931fe8f1f4517c9b9bc)

### Version 1.2

- Handle special characters in artifact filename correctly. [JENKINS-26858](https://issues.jenkins-ci.org/browse/JENKINS-26858)

### Version 1.1

- Supporting "download artifacts as ZIP".

### Version 1.0

- Initial version. See [JENKINS-6229](https://issues.jenkins-ci.org/browse/JENKINS-6229) for background. Not yet implemented: adding artifacts in multiple rounds; downloading ZIPs of artifacts.
