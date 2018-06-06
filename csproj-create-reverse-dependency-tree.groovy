import static groovy.json.JsonOutput.*;

def DEBUG = false
def file_output = 'output.deps'
def internal_only = System.getenv('internal_only').toBoolean()

println "..................................................................."
println "..................................................................."
println " === REVERSE DEPENDENCY TREE ==="
println "     (only first level)"
println "     Conf: file_output : ${file_output}"
println "     Param: internal_only : ${internal_only}"

def reverse_deps = [:]

def csproj_files = new FileNameFinder().getFileNames('./', '**/*.csproj')

csproj_files.each { f ->
  if (DEBUG) println "Looking in : ${f}"

  def xml = new XmlSlurper().parse(f)

  def name = xml.PropertyGroup.AssemblyName.text().trim()
  if (DEBUG) println "  -- Found Assembly: ${name}"

  // Reference tags
  def ref_tags = xml.'**'.findAll{
    it.name() == 'Reference' && it.attributes().containsKey('Include')
  }

  ref_tags.each { r ->
    def inc = r.attributes()['Include'].trim()
    // tokenize to take into account some refs have version info
    def dep = inc.tokenize(',')[0]
    if (DEBUG) println "    -- Reference Include ${dep}"

    if (!internal_only || (internal_only && !r.HintPath.isEmpty())) {
      if (!reverse_deps.containsKey(dep)) {
        reverse_deps[dep] = []
      }
      reverse_deps[dep] << name
    }
  }

  // Currently PackageReference is only used for 3rd Party packages
  // If this changes, this will need to be updated.
  if (!internal_only) {
    // PackageReference tags
    def pkgref_tags = xml.'**'.findAll{
      it.name() == 'PackageReference' && it.attributes().containsKey('Include')
    }

    pkgref_tags.each { r ->
      def dep = r.attributes()['Include'].trim()
      def ver = r.Version
      if (DEBUG) println "    -- PackageReference Include ${dep} (ver: ${ver})"

      def dep_ver = "${dep}-${ver}".toString()

      if (!reverse_deps.containsKey(dep_ver)) {
        reverse_deps[dep_ver] = []
      }
      reverse_deps[dep_ver] << name
    }
  }
}

reverse_deps = reverse_deps.sort()

File file = new File(file_output)
def writer = file.newWriter()

// We have the tree
writer << prettyPrint(toJson(reverse_deps))
writer.flush()
