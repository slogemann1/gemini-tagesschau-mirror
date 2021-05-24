import socket
import sys

PORT = 5555
LOCALHOST = '127.0.0.1'

binary_args = bytearray()
for arg in sys.argv:
    binary_args.extend(bytes(arg, 'utf-8'))
    binary_args.append(0)

client = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
client.connect((LOCALHOST, PORT))
client.sendall(binary_args)

exit_code = client.recv(1)
exit(exit_code[0])