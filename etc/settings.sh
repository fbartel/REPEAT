## Common settings sourced by executable scripts

topdir="$(cd "$(dirname "$BASH_SOURCE")/.." && pwd)"

# ------------------------------------------------------------------------------
# global constants -- all directory paths relative to topdir

bindir="bin"
libdir="lib"
etcdir="etc"
optdir="opt"             # directory of registration commands / symbolic links to installations
cfgdir="$etcdir/params"  # CSV tables with sets of registration parameters
setdir="$etcdir/dataset" # dataset Shell configuration files
vardir="var/cache"       # root directory for computed data
csvdir="var/table"       # summary tables of average quality measures

# path of MIRTK's "mirtk" executable, either absolute or relative to topdir
# recommended: on Linux, download MIRTK AppImage to "$optdir/" and "chmod +x $optdir/mirtk"
mirtk="$optdir/mirtk"

# path of directory containing ANTs installation, either absolute or relative to topdir
# recommended: create symbolic link "$optdir/ants" with absolute path to actual installation
#
# Required for preprocessing: "$ants/bin/N4BiasFieldCorrection"
ants="$optdir/ants"

# path of directory containing IRTK binaries, either absolute or relative to topdir
# recommended: create symbolic link "$optdir/irtk" with absolute path to actual installation
irtk="$optdir/irtk"

# path of directory containing NiftyReg installation, either absolute or relative to topdir
# recommended: create symbolic link "$optdir/niftyreg" with absolute path to actual installation
niftyreg="$optdir/niftyreg"

# path of directory containing elastix installation, either absolute or relative to topdir
# recommended: create symbolic link "$optdir/elastix" with absolute path to actual installation
elastix="$optdir/elastix"

# path of directory containing DRAMMS installation, either absolute or relative to topdir
# recommended: create symbolic link "$optdir/dramms" with absolute path to actual installation
dramms="$optdir/dramms"

# path of directory containing Demons executables, either absolute or relative to topdir
# recommended: create symbolic link "$optdir/demons/<command>" with absolute path to actual binaries
#
#
# Download:
# - DemonsRegistration and LogDomainDemonsRegistration:
#   http://www.insight-journal.org/browse/publication/154
# - LCCLogDomainDemonsRegistration (rpiLCClogDemons):
#   https://github.com/Inria-Asclepios/LCC-LogDemons
#
# Copy (or link) built binary files in the specified $demons directory:
# - "$demons/DemonsRegistration": Diffeomorphic, additive, and composite update; output displacement field
# - "$demons/LogDomainDemonsRegistration": Diffeomorphic; output stationary velocity field
# - "$demons/LCCLogDomainDemonsRegistration": Use LCC and different reguliarizations
#   - Original binary file is called "rpiLCClogDemons"
demons="$optdir/demons"

# when 'true', always compute all pairwise transformations even when
# registration method uses a symmetric energy function and thus the
# output of a given source to target registration may just be inverted
# to get the spatial mapping from the source to the target instead
#
# set 'true' when inverse consistency error should be evaluated
allsym=true

# when 'true', evaluate mean inverse consistency error
evlice=true

# when 'true', evaluate mean transitivity error
evlmte=false

# when 'true', evaluate Jacobian determinants
evljac=true

# when 'true', create table with runtime measurements
evltime=true

# force overwriting previously generated files, include also jobs in batch
# description that have been run before, i.e., although output files already exist
force=false

# force re-generation of batch job description files, possibly excluding jobs
# whose output files already exist unless force=true as well
update=true

# maximum no. of threads to use for each job
threads=8  # <0: use all available CPU cores

# HTCondor job execution environment
condor_getenv=true
condor_environment=
condor_requirements="Machine!=\"horatio.doc.ic.ac.uk\" && Machine!=\"plane.doc.ic.ac.uk\" && Machine!=\"quercus.doc.ic.ac.uk\""

# ------------------------------------------------------------------------------
# registration method/modality/channel specific settings

# whether transformations of registration method were computed beforehand
# or obtained elsewhere for a given dataset, e.g., from another study
use_existing_dofs()
{
  local dataset="$1"
  local regid="$2"
  # MAPER NiftyReg transformations computed for the original MICCAI'12 challenge
  # provided by Christian Ledig; these were computed using NiftyReg F3D with
  # FSL FAST tissue segmentations
  if [ "$dataset" = 'oasis' -a "$regid" = 'niftyreg-asym-maper-2012' ]; then
    echo true
  else
    echo false
  fi
}

# whether deformed images of registration method were computed beforehand
# or obtained elsewhere for a given dataset, e.g., from another study
use_existing_imgs()
{
  local dataset="$1"
  local regid="$2"
  # deformed T1-weighted images and segmentations for deformations
  # computed with ANTs SyN by the respective participants of the MICCAI'12
  # challenge provided by Christian Ledig
  if [ "$dataset" = 'oasis' -a "$regid" = 'ants-syn-2012' ]; then
    echo true
  else
    echo false
  fi
}

# whether registration method uses symmetric energy function
is_sym()
{
  if [ "${regid/-sym-/}" != "$regid" ]; then
    echo true
  elif [ "${regid:0:8}" = 'ants-syn' ]; then
    echo true
  else
    echo false
  fi
}

# whether registration method uses inverse consistent energy function
is_ic()
{
  if [ "${regid/-ic-/}" != "$regid" ]; then
    echo true
  else
    echo false
  fi
}

# get filename extension of transformation files
get_dofsuf()
{
  local toolkit="$(get_toolkit "$1")"
  if [ "${1:0:18}" = 'niftyreg-sym-svffd' ]; then
    # deformed images slightly differ when using reg_resample or convert-dof output file
    # hence, use reg_resample for now to apply the output SVFFD of NiftyReg
    echo ".nii.gz"
  elif [ "$1" = 'affine' -o \
       "$toolkit" = 'mirtk' -o \
       "$toolkit" = 'irtk' -o \
       "$toolkit" = 'niftyreg' -o \
       "$toolkit" = 'elastix' -o \
       "$toolkit" = 'dramms' -o \
       "$toolkit" = 'demons' ]; then
    echo ".dof.gz"  # non-[M]IRTK output files converted to .dof.gz format using MIRTK convert-dof
  elif [ "$toolkit" = 'niftyreg' ]; then
    echo ".nii.gz"  # unused, but saved anyway
  elif [ "$toolkit" = 'elastix' ]; then
    echo ".txt"     # unused, but saved anyway
  elif [ "$toolkit" = 'dramms' ]; then
    echo ".nii.gz"  # unused, but saved anyway
  elif [ "$toolkit" = 'demons' ]; then
    echo ".nii.gz"  # unused, but saved anyway
  elif [ "$toolkit" = 'ants' ]; then
    echo ".nii.gz"
  else
    error "Unknown registration toolkit: $toolkit! Modify get_dofsuf() in $BASH_SOURCE."
  fi
}

# get evaluation measures for given modality/channel/contrast
get_measures()
{
  if [ "$(is_seg "$1")" = true ]; then
    echo "dsc entropy"
  elif [ "$(is_prob "$1")" = true -o "$(is_mask "$1")" = true ]; then
    echo "dsc"
  else
    echo "sdev entropy"
  fi
}

# ------------------------------------------------------------------------------
# auxiliary functions
. "$topdir/$libdir/utils.sh" || exit 1  # e.g., error() function
