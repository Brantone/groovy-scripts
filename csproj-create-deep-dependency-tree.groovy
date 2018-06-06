import static groovy.json.JsonOutput.*;

DEBUG = false
MAX_DEPTH = 20
def file_output_shallow = 'output_shallow.deps'
def file_output_deep = 'output_deep.deps'
def internal_only = System.getenv('internal_only').toBoolean()

println "..................................................................."
println "..................................................................."
println " === DEPENDENCY TREE ==="
println "     Conf: MAX_DEPTH : ${MAX_DEPTH}"
println "     Conf: file_output_shallow : ${file_output_shallow}"
println "     Conf: file_output_deep : ${file_output_deep}"
println "     Param: internal_only : ${internal_only}"

// No 'def' because want it global to function below
tmp_tree = [:]

def csproj_files = new FileNameFinder().getFileNames('./', '**/*.csproj')

if (DEBUG) println "Found ${csproj_files.size()} files ..."

csproj_files.each { f ->
  if (DEBUG) println "Looking in : ${f}"

  def xml = new XmlSlurper().parse(f)

  def name = xml.PropertyGroup.AssemblyName.text().trim()
  if (DEBUG) println "  -- Found Assembly: ${name}"

  if (!tmp_tree.containsKey(name)) {
    tmp_tree[name] = []
  }

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
      tmp_tree[name] << dep
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

      tmp_tree[name] << dep_ver
    }
  }
}

tmp_tree = tmp_tree.sort()

File file = new File(file_output_shallow)
def writer = file.newWriter()

// Tmp tree
writer << prettyPrint(toJson(tmp_tree))
writer.flush()

def fill_tree(i, node_list) {

  if (node_list.size() == 1) {
    return node_list
  }
  else if (node_list.size() == 0) {
    return ""
  }
  else if (i >= MAX_DEPTH) {
    ret_val = ["MAX DEPTH WAS REACHED!"]
    ret_val << node_list
    return ret_val
  }


  def node_tree = [:]
  node_list.each { n ->

    if (!node_tree.containsKey(n)) {
      node_tree[n] = [:]
    }

    if (tmp_tree.containsKey(n)) {
      node_tree[n] = fill_tree(++i, tmp_tree[n])
    }
    else {
      node_tree[n] = ""
    }
  }

  return node_tree
}

def dep_tree = [:]
tmp_tree.each { n, childen ->
  dep_tree[n] = fill_tree(0, childen)
}

file = new File(file_output_deep)
writer = file.newWriter()

// We have the tree
writer << prettyPrint(toJson(dep_tree))
writer.flush()
