import hudson.FilePath
import jenkins.model.Jenkins
import com.cloudbees.hudson.plugins.folder.*

Jenkins jkns = Jenkins.instance
def build_vars = build.buildVariables

def base_folder_name = build_vars['base_folder_name']
def build_proj_file = 'ServerBuild.proj'
def tier = (build_vars['assemblies_tier'].toInteger() - 1)

// Ensure base folder exists
def base_folder = jkns.getItemByFullName(base_folder_name)
if (base_folder == null) {
  base_folder = jkns.createProject(Folder.class, base_folder_name)
}

FilePath build_proj_fp = build.workspace.child(build_proj_file)

if (!build_proj_fp.exists()) {
  println "ERROR!!"
  exit 0
}

def xml = new XmlSlurper().parseText(build_proj_fp.readToString())

def msbuild_tags = xml.'**'.find{
    it.name() == 'Target' && it.attributes()['Name'] == 'Assemblies'
  }.MSBuild

def projects = msbuild_tags[tier].@"Projects".text().trim().tokenize(';')

projects.each { proj ->
  // 2nd spot should have what we want
  def name = proj.trim().tokenize('\\')[2]
  println "Checking if ${name} folder exists in ${base_folder.getName()} ..."

  def folderName = "${base_folder.getName()}/${name}"
  def folder = jkns.getItemByFullName(folderName)

  if (folder == null) {
    folder = base_folder.createProject(Folder.class, name)
  }
}

return 0
