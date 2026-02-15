import requests
import time
import sys
import json

BASE_URL = "http://localhost:8080/api"

def verify():
    # 1. Upload Document with custom models
    print("Uploading document with custom models...")
    # Create a dummy file if not exists
    with open('test_dynamic_model.txt', 'w') as f:
        f.write("This is a test document about artificial intelligence and dynamic models.")

    files = {'file': ('test_dynamic_model.txt', open('test_dynamic_model.txt', 'rb'), 'text/plain')}
    # Use models that fit in 8GB RAM with Docker overhead
    params = {'title': 'Dynamic Model Test', 'extractionModel': 'tinyllama', 'embeddingModel': 'all-minilm'}
    
    try:
        print(f"Sending params: {params}")
        resp = requests.post(f"{BASE_URL}/documents/upload", files=files, params=params)
        if resp.status_code != 202:
            print(f"Upload failed: {resp.status_code} {resp.text}")
            sys.exit(1)
            
        data = resp.json()
        doc_id = data['documentId']
        task_id = data['taskId']
        print(f"Upload accepted. Task ID: {task_id}")
        
    except Exception as e:
        print(f"Exception during upload: {e}")
        sys.exit(1)

    # 2. Poll Task Status
    print("Polling task status...")
    for _ in range(30):
        resp = requests.get(f"{BASE_URL}/tasks/{task_id}/status")
        status_data = resp.json()
        status = status_data['status']
        print(f"Task Status: {status}")
        
        if status == 'COMPLETED':
            print("Ingestion completed.")
            break
        elif status == 'FAILED':
            print(f"Ingestion failed: {status_data.get('errorMessage')}")
            sys.exit(1)
            
        time.sleep(2)
    else:
        print("Timeout waiting for ingestion.")
        sys.exit(1)

    # 3. Create Chat
    print("Creating chat...")
    try:
        # Notebook ID is fixed in DocumentService for now: 00000000-0000-0000-0000-000000000001
        notebook_id = "00000000-0000-0000-0000-000000000001"
        
        resp = requests.post(f"{BASE_URL}/notebooks/{notebook_id}/chats", json={"title": "Test Chat"})
        if resp.status_code != 201:
             print(f"Create Chat failed: {resp.status_code} {resp.text}")
             sys.exit(1)
             
        chat_id = resp.json()['id']
        print(f"Chat created: {chat_id}")
        
    except Exception as e:
        print(f"Exception during chat creation: {e}")
        sys.exit(1)

    # 4. Send Message with specific model
    print("Sending message with model='tinyllama'...")
    try:
        payload = {"content": "What is this document about?", "model": "tinyllama"}
        resp = requests.post(f"{BASE_URL}/chats/{chat_id}/messages", json=payload)
        
        if resp.status_code != 200:
            print(f"Message failed: {resp.status_code} {resp.text}")
            sys.exit(1)
            
        answer = resp.json()['content']
        print(f"Answer received: {answer[:100]}...")
        print("Verification SUCCESS")

    except Exception as e:
        print(f"Exception during chat: {e}")
        sys.exit(1)

if __name__ == "__main__":
    verify()
