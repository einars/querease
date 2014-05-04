name := "querease"

scalaVersion := "2.11.0"

scalacOptions ++= Seq("-deprecation", "-feature")

retrieveManaged := true

resolvers ++= Seq(
  "snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
)

libraryDependencies ++= Seq(
  "org.tresql" %% "tresql" % "6.0-M2-SNAPSHOT",
  "mojoz" %% "mojoz" % "0.1-SNAPSHOT",
  // test
  "org.scalatest" % "scalatest_2.10" % "2.0.M8" % "test"
)

scalaSource in Compile <<= baseDirectory(_ / "src")

scalacOptions in (Compile, doc) <++= (baseDirectory in
 LocalProject("querease")).map {
   bd => Seq("-sourcepath", bd.getAbsolutePath,
             "-doc-source-url", "https://github.com/guntiso/querease/blob/develop€{FILE_PATH}.scala")
 }
