import importlib.util
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]

class ArtifactVerification(unittest.TestCase):
    def test_identity_and_signer_reject_wrong_artifacts(self):
        path = ROOT/'tools/verify_apk.py'
        self.assertTrue(path.is_file(), 'Artifact verifier missing')
        spec = importlib.util.spec_from_file_location('verify_apk', path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        metadata = {'applicationId': 'com.saebm.btcnfccustomer.debug', 'elements': [
            {'versionCode': 1, 'versionName': '0.1.0-test-only', 'outputFile': 'BTC-Tag-Checker-debug.apk'}]}
        badging = "package: name='com.saebm.btcnfccustomer.debug' versionCode='1' versionName='0.1.0-test-only'\napplication-label:'BTC Tag Checker (Debug)'\n"
        module.validate_identity(metadata, badging, 'debug')
        for old, new in [('com.saebm.btcnfccustomer.debug', 'wrong.id'), ("versionCode='1'", "versionCode='2'"), ('BTC Tag Checker (Debug)', 'Old Name')]:
            with self.subTest(old=old), self.assertRaises(ValueError):
                module.validate_identity(metadata, badging.replace(old, new), 'debug')
        cert = 'a'*64
        module.validate_signer('Signer #1 certificate SHA-256 digest: '+cert, cert)
        for value in ['b'*64, '', cert+'\nSigner #2 certificate SHA-256 digest: '+'b'*64]:
            with self.subTest(value=value), self.assertRaises(ValueError):
                module.validate_signer('Signer #1 certificate SHA-256 digest: '+value, cert)

if __name__ == '__main__':
    unittest.main()
