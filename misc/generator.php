<?

/*
 * ComicsReader is an Android application to read comics
 * Copyright (C) 2011-2012 Cedric OCHS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

// currently only supports CBZ files

// display all PHP errors
ini_set("display_errors","1");
error_reporting(E_ALL);

function encodeURI($url, $html)
{
	// http://php.net/manual/en/function.rawurlencode.php
	// https://developer.mozilla.org/en/JavaScript/Reference/Global_Objects/encodeURI
	$reserved = array('%2D'=>'-','%5F'=>'_','%2E'=>'.','%21'=>'!', 
		'%2A'=>'*', '%27'=>"'", '%28'=>'(', '%29'=>')');

	$unescaped = array('%3B'=>';','%2C'=>',','%2F'=>'/','%3F'=>'?','%3A'=>':',
		'%40'=>'@','%3D'=>'=','%2B'=>'+','%24'=>'$');

	$score = array('%23'=>'#');

	return strtr(rawurlencode($url), $html ? array_merge($reserved, $unescaped):array_merge($reserved, $unescaped, $score));
}

function encodeTitle($title)
{
	return trim(strtr($title, array('_' => ' ')));
}

function cmpAlbums($a, $b)
{
	$a = strtolower($a['title']);
	$b = strtolower($b['title']);

	if ($a == $b) return 0;

	return $a < $b ? -1 : 1;
}

function xmlentities($xml)
{
	return str_replace(array("&"), array("&amp;"), $xml);
}

class CbzArchive
{
	private $files = array();
	private $local = '';
	private $remote = '';
	private $albums_directory = '';
	private $thumbnails_directory = '';

	function __construct($folder='', $thumbnails='thumbnails')
	{
		// take local directory based on current script location
		$local = dirname($_SERVER["SCRIPT_FILENAME"]);
		$remote = $_SERVER["HTTP_HOST"].$_SERVER['PHP_SELF'];

		$pos = strrpos($remote, "/");
		if ($pos) $remote = substr($remote, 0, $pos);

		$this->local = $local;
		$this->remote = $remote;
		$this->albums_directory = $folder;
		$this->thumbnails_directory = $thumbnails;
	}
	
	function __destruct()
	{
	}
	
	function open()
	{
		$this->files = $this->findFiles();
	}
	
	function findFiles($dir='')
	{
		$ret = array();
		
		if (!file_exists($this->albums_directory.'/'.$dir))
		{
			die("Folder ".$this->albums_directory.'/'.$dir." doesn't exist");
		}
	
		$files = opendir($this->albums_directory.'/'.$dir);
		
		if (!$files) return false;
		
		while ($file = readdir($files))
		{
			if (!preg_match("#^\.#", $file))
			{
				$current = ($dir ? $dir.'/':'').$file;

				$tmp = array();
				
				if (is_dir($this->albums_directory.'/'.$current))
				{
					$rr = $this->findFiles($current);
					
					if (count($rr) > 0)
					{
						$tmp['fullpath'] = $this->albums_directory.'/'.$current;
						$tmp['filename'] = $file;
						$tmp['title'] = encodeTitle($file);
						$tmp['size'] = 0;
						$tmp['files'] = $rr;
					}
				}
				else if (preg_match("#^((.+)\\.cb[rz])$#", $file, $regs))
				{
					$tmp['fullpath'] = $this->albums_directory.'/'.$current;
					$tmp['filename'] = $file;
					$tmp['title'] = encodeTitle($regs[2]);
					$tmp['size'] = filesize($this->albums_directory.'/'.$current);
				}

				if ($tmp) $ret[] = $tmp;
			}
		}

		closedir($files);

		unset($files);
		
		usort($ret, "cmpAlbums");

		return $ret;
	}
	
	function createThumbnails(&$files='')
	{
		// thumbnails creation
		@mkdir($this->thumbnails_directory);
		
		if (!$files) $files = &$this->files;

		foreach($files as $i => &$file)
		{
			if (isset($file['files']) && count($file['files']))
			{
				$this->createThumbnails($file['files']);
			}
			else
			{
				$file['md5'] = $this->createThumbnail($this->local."/".$file['fullpath']);

				if ($file['md5'])
				{
					$file['thumbnail'] = $this->thumbnails_directory."/".$file['md5'].".png";
				}
				else
				{
					$file['thumbnail'] = "";
				}
			}
		}
		
		return true;
	}

	function createThumbnail($file)
	{
		$zip = new ZipArchive();
		
		if ($zip->open($file) != TRUE)
		{
			return "";
		}

		$files = array();
		
		for ($i=0; $i < $zip->numFiles; ++$i)
		{
			$stat = $zip->statIndex($i);
			$files[] = $stat['name'];
		}
		
		if (!count($files)) return "";

		sort($files);
		
		$content = $zip->getFromName($files[0]);
		
		$md5 = md5($content);

		$zip->close();
		
		$filename = $this->thumbnails_directory."/$md5.png";
		
		if (file_exists($filename))
		{
			unset($content);

			return $md5;
		}
		
		// load image and get image size
		$img = imagecreatefromstring($content);
		
		unset($content);
		
		$width = imagesx($img);
		$height = imagesy($img);

		// calculate thumbnail size
		$new_height = 96;
		$new_width = floor($width * $new_height / $height);

		// create a new temporary image
		$tmp_img = imagecreatetruecolor($new_width, $new_height);

		// copy and resize old image into new image 
		if (imagecopyresampled($tmp_img, $img, 0, 0, 0, 0, $new_width, $new_height, $width, $height))
		{
			unset($img);

			// save thumbnail into a file
			imagepng($tmp_img, $filename, 9);
		}
		
		return $md5;
	}

	function createHtmlIndex($filename, $files='', $parent='')
	{
		if (!$files) $files = $this->files;

		$f = fopen($filename, "w");
		
		if (!$f) return false;
		
		fwrite($f, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		fwrite($f, "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">\n");
		fwrite($f, "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\">\n");
		fwrite($f, "\t<head>\n");
		fwrite($f, "\t\t<title>Gallery</title>\n");
		fwrite($f, "\t\t<link href=\"style.css\" media=\"all\" rel=\"stylesheet\" type=\"text/css\" />\n");
		fwrite($f, "\t</head>\n");
		fwrite($f, "<body>\n");

		if ($parent)
		{
			fwrite($f, "\t<div class=\"album\">\n");
			fwrite($f, "\t\t<div class=\"thumbnail\">\n");
			fwrite($f, "\t\t\t<a href=\"".encodeURI($parent, true)."\">\n");
			fwrite($f, "\t\t\t\t<img src=\"folder.png\" alt=\"Parent\" />\n");
			fwrite($f, "\t\t\t</a>\n");
			fwrite($f, "\t\t</div>\n");
			fwrite($f, "\t\t<div class=\"title\">..</div>\n");
			fwrite($f, "\t</div>\n");
		}
	
		foreach($files as $file)
		{
			if (isset($file['md5']) && $file['md5'])
			{
				fwrite($f, "\t<div class=\"album\">\n");
				fwrite($f, "\t\t<div class=\"thumbnail\">\n");
				fwrite($f, "\t\t\t<a href=\"".encodeURI($file['fullpath'], true)."\">\n");
				fwrite($f, "\t\t\t\t<img src=\"".$file['thumbnail']."\" alt=\"".$file['title']."\" />\n");
				fwrite($f, "\t\t\t</a>\n");
				fwrite($f, "\t\t</div>\n");
				fwrite($f, "\t\t<div class=\"title\">".$file['title']."</div>\n");
				fwrite($f, "\t</div>\n");
			}
			else if (isset($file['files']) && count($file['files']))
			{
				$pos = strpos($filename, '.');
				$extension = $pos > 0 ? substr($filename, $pos):"";

				$child = str_replace("/", "_", $file['fullpath']).$extension;
			
				if ($this->createHtmlIndex($child, $file['files'], $filename))
				{
					fwrite($f, "\t<div class=\"album\">\n");
					fwrite($f, "\t\t<div class=\"thumbnail\">\n");
					fwrite($f, "\t\t\t<a href=\"$child\">\n");
					fwrite($f, "\t\t\t\t<img src=\"folder.png\" alt=\"".$file['title']."\" />\n");
					fwrite($f, "\t\t\t</a>\n");
					fwrite($f, "\t\t</div>\n");
					fwrite($f, "\t\t<div class=\"title\">".$file['title']."</div>\n");
					fwrite($f, "\t</div>\n");
				}
			}
		}

		fwrite($f, "</body>\n");
		fwrite($f, "</html>\n");
		
		fclose($f);
		
		return true;
	}

	function createXmlIndex($filename, $files='', $parent='')
	{
		if (!$files) $files = $this->files;

		$f = fopen($filename, "w");
		
		if (!$f) return false;
		
		fwrite($f, "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
		fwrite($f, "<albums>\n");
		
		if ($parent)
		{
			fwrite($f, "\t<folder>\n");
			fwrite($f, "\t\t<title>..</title>\n");
			fwrite($f, "\t\t<url>".encodeURI($this->remote.'/'.$parent, false)."</url>\n");
			fwrite($f, "\t</folder>\n");
		}
		
		foreach($files as $file)
		{
			if (isset($file['md5']) && $file['md5'])
			{
				fwrite($f, "\t<album>\n");
				fwrite($f, "\t\t<title>".xmlentities($file['title'])."</title>\n");
				fwrite($f, "\t\t<filename>".xmlentities($file['filename'])."</filename>\n");
				fwrite($f, "\t\t<size>".$file['size']."</size>\n");
				fwrite($f, "\t\t<thumbnail>".$this->remote.'/'.$file['thumbnail']."</thumbnail>\n");
				fwrite($f, "\t\t<url>".encodeURI($this->remote.'/'.$file['fullpath'], false)."</url>\n");
				fwrite($f, "\t</album>\n");
			}
			else if (isset($file['files']) && count($file['files']))
			{
				$pos = strpos($filename, '.');
				$extension = $pos > 0 ? substr($filename, $pos):"";

				$child = str_replace("/", "_", $file['fullpath']).$extension;
			
				if ($this->createXmlIndex($child, $file['files'], $filename))
				{
					fwrite($f, "\t<folder>\n");
					fwrite($f, "\t\t<title>".xmlentities($file['title'])."</title>\n");
					fwrite($f, "\t\t<url>".encodeURI($this->remote.'/'.$child, false)."</url>\n");
					fwrite($f, "\t</folder>\n");
				}
			}
		}

		fwrite($f, "</albums>\n");
		
		fclose($f);
		
		return true;
	}

	function createJsonIndex($filename, $files='', $parent='')
	{
		if (!$files) $files = $this->files;

		$f = fopen($filename, "w");
		
		if (!$f) return false;
		
		fwrite($f, "{\"albums\": {\n");

		$i = 0;
		$folders_count = $parent ? 1:0;

		foreach($files as $file)
		{
			if (isset($file['files']) && count($file['files']) && !isset($file['md5'])) ++$folders_count;
		}
		
		if ($folders_count > 0)
		{
			fwrite($f, "\t\"folder\": [");
		
			if ($parent)
			{
				++$i;
				fwrite($f, "\n\t\t{\"title\": \"..\", \"url\": \"".encodeURI($this->remote.'/'.$parent, false)."\"}");
			}

			foreach($files as $file)
			{
				if (isset($file['files']) && count($file['files']) && !isset($file['md5']))
				{
					$pos = strpos($filename, '.');
					$extension = $pos > 0 ? substr($filename, $pos):"";

					$child = str_replace("/", "_", $file['fullpath']).$extension;
			
					if ($this->createJsonIndex($child, $file['files'], $filename))
					{
						if ($i++) fwrite($f, ",");
						fwrite($f, "\n\t\t{\"title\": \"".xmlentities($file['title'])."\", \"url\": \"".encodeURI($this->remote.'/'.$child, false)."\"}");
					}
				}
			}

			fwrite($f, "\n\t]");
		}

		$i = 0;
		$files_count = 0;

		foreach($files as $file)
		{
			if (isset($file['md5']) && $file['md5']) ++$files_count;
		}

		if ($files_count > 0)
		{
			if ($folders_count > 0)
			{
				fwrite($f, ",\n");
			}
		
			fwrite($f, "\t\"album\": [");

			foreach($files as $file)
			{
				if (isset($file['md5']) && $file['md5'])
				{
					if ($i++) fwrite($f, ",");
					fwrite($f, "\n\t\t{\"title\": \"".xmlentities($file['title'])."\", \"filename\": \"".xmlentities($file['filename'])."\", \"size\": ".$file['size'].", \"thumbnail\": \"".$this->remote.'/'.$file['thumbnail']."\", \"url\": \"".encodeURI($this->remote.'/'.$file['fullpath'], false)."\"}");
				}
			}

			fwrite($f, "\n\t]\n");
		}

		fwrite($f, "}}\n");

		fclose($f);
		
		return true;
	}
};

$cli = isset($argc) && ($argc > 0);

if ($cli)
{
	print "ComicsReader remote albums index generator\n";
	
	if ($argc < 2)
	{
		print "$argv[0] [options] <url>\n";
		print "\n";
		print "\t-da <albums directory>\n";
		print "\t-dt <thumbnails directory>\n";
		print "\t-fx <xml filename>\n";
		print "\t-fj <json filename>\n";
		print "\t-fh <html filename>\n";
		print "\t-x generate XML index\n";
		print "\t-j generate JSON index\n";
		print "\t-h generate HTML index\n";
	}

	for($i = 1; $i < $argc; ++$i)
	{
		$arg = $argv[$i];
		$next = isset($argv[$i+1]) ? $argv[$i+1]:'';
		
		if ($arg[0] == '-')
		{
			$arg = substr($arg, 1);

			switch($arg)
			{
				case 'da': $_POST['albums_directory'] = $next; ++$i; break;
				case 'dt': $_POST['thumbnails_directory'] = $next; ++$i; break;
				case 'fx': $_POST['xml_filename'] = $next; ++$i; break;
				case 'fj': $_POST['json_filename'] = $next; ++$i; break;
				case 'fh': $_POST['html_filename'] = $next; ++$i; break;
				case 'x': $_POST['generate_xml'] = 1; break;
				case 'j': $_POST['generate_json'] = 1; break;
				case 'h': $_POST['generate_html'] = 1; break;
				default: print "Unknown option -$arg\n";
			}
		}
		else
		{
			$_SERVER["HTTP_HOST"] = $arg.'/';
		}
	}

	$_POST['generate'] = isset($_POST['generate_xml']) || isset($_POST['generate_json']) || isset($_POST['generate_html']) ? 1:0;
}

function get($var)
{
	if (isset($_POST[$var])) return $_POST[$var];
	
	return "";
}

$albums_directory = get("albums_directory");
if (!$albums_directory) $albums_directory = "albums";

$thumbnails_directory = get("thumbnails_directory");
if (!$thumbnails_directory) $thumbnails_directory = "thumbnails";

$xml_filename = get("xml_filename");
if (!$xml_filename) $xml_filename = "index.xml";

$json_filename = get("json_filename");
if (!$json_filename) $json_filename = "index.json";

$html_filename = get("html_filename");
if (!$html_filename) $html_filename = "index.htm";

if (get("generate") == "1")
{
	$generate_xml = get("generate_xml");
	$generate_json = get("generate_json");
	$generate_html = get("generate_html");

	$cbz = new CbzArchive($albums_directory, $thumbnails_directory);
	$cbz->open();
	$cbz->createThumbnails();

	if ($generate_xml)
	{
		$cbz->createXmlIndex($xml_filename);
	}

	if ($generate_json)
	{
		$cbz->createJsonIndex($json_filename);
	}
	
	if ($generate_html)
	{
		$cbz->createHtmlIndex($html_filename);
	}
}
else
{
	$generate_xml = 1;
	$generate_json = 1;
	$generate_html = 1;
}

if ($cli)
{
	return 0;
}
else
{

?>
<html>
<head><title>ComicsReader remote albums index generator</title></head>
<body>
<form action="<?=$_SERVER['PHP_SELF']?>" method="post">
<input name="generate" type="hidden" value="1" />
<div>Directory where to search for CBZ files<br/><input name="albums_directory" type="text" value="<?=$albums_directory?>" /></div>
<div>Directory for thumbnails<br/><input name="thumbnails_directory" type="text" value="<?=$thumbnails_directory?>" /></div>
<div><input name="generate_html" type="checkbox" value="1" <?=$generate_html ? "checked ":""?>/>Generate HTML index</div>
<div>Filename for HTML index (don't forget an extension)<br/><input name="html_filename" type="text" value="<?=$html_filename?>" /></div>
<div><input name="generate_xml" type="checkbox" value="1" <?=$generate_xml ? "checked ":""?>/>Generate XML index</div>
<div>Filename for XML index (don't forget an extension)<br/><input name="xml_filename" type="text" value="<?=$xml_filename?>" /></div>
<div><input name="generate_json" type="checkbox" value="1" <?=$generate_json ? "checked ":""?>/>Generate JSON index</div>
<div>Filename for JSON index (don't forget an extension)<br/><input name="json_filename" type="text" value="<?=$json_filename?>" /></div>
<div><input name="submit" type="submit" value="Ok" /></div>
</form>
</body>
</html>
<?

}

?>
